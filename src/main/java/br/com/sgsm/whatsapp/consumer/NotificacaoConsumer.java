package br.com.sgsm.whatsapp.consumer;

import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.Paciente;
import br.com.sgsm.repository.AgendamentoRepository;
import br.com.sgsm.repository.MedicoRepository;
import br.com.sgsm.repository.PacienteRepository;
import br.com.sgsm.repository.ServicoMedicoRepository;
import br.com.sgsm.whatsapp.client.WhatsAppService;
import br.com.sgsm.whatsapp.service.ContatoPacienteService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

// Consome sgsm:events:notificacao (Redis PAPEL 3) e envia notificacoes proativas via WhatsApp.
// Mesmo padrao do VetorizacaoConsumer do sgsm-ia: consumer group + XACK, sem ACK = retry
// automatico. Toda notificacao respeita o opt-out do paciente (secao 10.2 do desenho).
@Component
public class NotificacaoConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoConsumer.class);
    private static final String STREAM_KEY = "sgsm:events:notificacao";
    private static final String GROUP = "sgsm-notificacao-group";
    private static final String CONSUMER = "sgsm-notificacao-consumer-1";
    private static final DateTimeFormatter FORMATADOR_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final StringRedisTemplate redis;
    private final PacienteRepository pacienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final MedicoRepository medicoRepository;
    private final ServicoMedicoRepository servicoMedicoRepository;
    private final WhatsAppService whatsAppService;
    private final ContatoPacienteService contatoPacienteService;

    public NotificacaoConsumer(StringRedisTemplate redis,
                               PacienteRepository pacienteRepository,
                               AgendamentoRepository agendamentoRepository,
                               MedicoRepository medicoRepository,
                               ServicoMedicoRepository servicoMedicoRepository,
                               WhatsAppService whatsAppService,
                               ContatoPacienteService contatoPacienteService) {
        this.redis = redis;
        this.pacienteRepository = pacienteRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.medicoRepository = medicoRepository;
        this.servicoMedicoRepository = servicoMedicoRepository;
        this.whatsAppService = whatsAppService;
        this.contatoPacienteService = contatoPacienteService;
    }

    @PostConstruct
    public void inicializarConsumerGroup() {
        try {
            redis.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
            log.info("Consumer group '{}' criado no stream '{}'", GROUP, STREAM_KEY);
        } catch (Exception e) {
            log.debug("Consumer group ja existe: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 500)
    public void processar() {
        try {
            List<MapRecord<String, Object, Object>> mensagens = redis.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(10).block(Duration.ofMillis(200)),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );

            if (mensagens == null || mensagens.isEmpty()) return;

            for (MapRecord<String, Object, Object> msg : mensagens) {
                String tipoNotificacao = (String) msg.getValue().get("tipoNotificacao");
                String agendamentoId = (String) msg.getValue().get("agendamentoId");
                String pacienteId = (String) msg.getValue().get("pacienteId");
                try {
                    processarNotificacao(tipoNotificacao, agendamentoId, pacienteId);
                    redis.opsForStream().acknowledge(STREAM_KEY, GROUP, msg.getId());
                } catch (Exception e) {
                    log.warn("Falha ao processar notificacao tipo={} pacienteId={}. Sera reprocessada. Erro: {}",
                            tipoNotificacao, pacienteId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Erro no loop do consumer de notificacao: {}", e.getMessage());
        }
    }

    private void processarNotificacao(String tipoNotificacao, String agendamentoId, String pacienteId) {
        Paciente paciente = pacienteRepository.findById(UUID.fromString(pacienteId)).orElse(null);
        if (paciente == null || Boolean.TRUE.equals(paciente.getWhatsappOptOut()) || paciente.getTelefone() == null) {
            log.debug("Notificacao {} ignorada para paciente {}: sem telefone ou opt-out", tipoNotificacao, pacienteId);
            return;
        }

        String mensagem = switch (tipoNotificacao) {
            case "CONFIRMACAO_AGENDAMENTO" -> montarConfirmacao(agendamentoId, paciente);
            case "LEMBRETE_CONSULTA" -> montarLembrete(agendamentoId, paciente);
            case "RECUPERACAO_INATIVO" -> montarRecuperacao(paciente);
            default -> null;
        };
        if (mensagem == null) {
            return;
        }

        whatsAppService.enviarTexto(paciente.getTelefone(), mensagem);
        contatoPacienteService.registrarSaida(pacienteId, mensagem);
    }

    private String montarConfirmacao(String agendamentoId, Paciente paciente) {
        Agendamento agendamento = buscarAgendamento(agendamentoId);
        if (agendamento == null) return null;
        return "Olá, %s! Sua consulta de %s com %s foi confirmada para %s.".formatted(
                paciente.getNome(), nomeServico(agendamento), nomeMedico(agendamento),
                FORMATADOR_DATA_HORA.format(agendamento.getDataHoraInicio()));
    }

    private String montarLembrete(String agendamentoId, Paciente paciente) {
        Agendamento agendamento = buscarAgendamento(agendamentoId);
        if (agendamento == null) return null;
        return "Lembrete: você tem uma consulta de %s com %s em %s. Contamos com você!".formatted(
                nomeServico(agendamento), nomeMedico(agendamento),
                FORMATADOR_DATA_HORA.format(agendamento.getDataHoraInicio()));
    }

    private String montarRecuperacao(Paciente paciente) {
        return "Olá, %s! Sentimos sua falta por aqui. Que tal agendar uma nova consulta? Estamos à disposição."
                .formatted(paciente.getNome());
    }

    private Agendamento buscarAgendamento(String agendamentoId) {
        if (agendamentoId == null || agendamentoId.isBlank()) return null;
        return agendamentoRepository.findById(UUID.fromString(agendamentoId)).orElse(null);
    }

    private String nomeMedico(Agendamento agendamento) {
        return medicoRepository.findById(agendamento.getMedicoId())
                .map(br.com.sgsm.domain.Medico::getNome).orElse("seu médico");
    }

    private String nomeServico(Agendamento agendamento) {
        return servicoMedicoRepository.findById(agendamento.getServicoMedicoId())
                .map(br.com.sgsm.domain.ServicoMedico::getNome).orElse("consulta");
    }
}
