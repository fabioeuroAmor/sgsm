package br.com.sgsm.whatsapp.scheduler;

import br.com.sgsm.domain.StatusAgendamento;
import br.com.sgsm.events.NotificacaoPublisher;
import br.com.sgsm.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

// Dispara os dois tipos de notificacao proativa "agendadas" da secao 10 (a terceira,
// CONFIRMACAO_AGENDAMENTO, e disparada direto por AgendamentoService quando o status muda).
@Component
public class NotificacaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoScheduler.class);

    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoPublisher notificacaoPublisher;
    private final JdbcTemplate jdbc;

    public NotificacaoScheduler(AgendamentoRepository agendamentoRepository,
                                NotificacaoPublisher notificacaoPublisher,
                                JdbcTemplate jdbc) {
        this.agendamentoRepository = agendamentoRepository;
        this.notificacaoPublisher = notificacaoPublisher;
        this.jdbc = jdbc;
    }

    // Roda a cada 30 min: cada agendamento CONFIRMADO cai numa unica janela de 30 min antes de
    // completar 24h/2h para o inicio. LIMITACAO CONHECIDA: nao ha tabela de deduplicacao
    // dedicada (fora do escopo desta versao) -- se o job ficar fora do ar durante uma janela,
    // aquele lembrete especifico e perdido, nao reenviado depois.
    @Scheduled(cron = "0 0/30 * * * *")
    public void enviarLembretesDeConsulta() {
        publicarLembretesNaJanela(Duration.ofHours(24));
        publicarLembretesNaJanela(Duration.ofHours(2));
    }

    private void publicarLembretesNaJanela(Duration antecedencia) {
        OffsetDateTime inicio = OffsetDateTime.now().plus(antecedencia);
        OffsetDateTime fim = inicio.plusMinutes(30);
        var agendamentos = agendamentoRepository.findAllByStatusAndDataHoraInicioBetween(
                StatusAgendamento.CONFIRMADO, inicio, fim);
        for (var agendamento : agendamentos) {
            notificacaoPublisher.publicar("LEMBRETE_CONSULTA",
                    agendamento.getId().toString(), agendamento.getPacienteId().toString());
        }
    }

    // Roda semanalmente (nao diariamente): sem uma tabela de controle de envio (fora do escopo
    // desta versao), rodar todo dia mandaria a mesma mensagem de recuperacao repetidas vezes
    // pro mesmo paciente enquanto ele continuar inativo -- semanal reduz o incomodo.
    @Scheduled(cron = "0 0 8 * * MON")
    public void recuperarPacientesInativos() {
        List<String> pacienteIds = jdbc.queryForList(
                "SELECT paciente_id::text FROM crm.v_churn_risco", String.class);
        log.info("Recuperação de inativos: {} pacientes elegíveis", pacienteIds.size());
        for (String pacienteId : pacienteIds) {
            notificacaoPublisher.publicar("RECUPERACAO_INATIVO", null, pacienteId);
        }
    }
}
