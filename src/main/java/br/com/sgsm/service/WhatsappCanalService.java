package br.com.sgsm.service;

import br.com.sgsm.client.EvolutionInstanciaClient;
import br.com.sgsm.domain.WhatsappCanal;
import br.com.sgsm.dto.WhatsappCanalInternoResponse;
import br.com.sgsm.dto.WhatsappCanalResponse;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.exception.ServicoIndisponivelException;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.WhatsappCanalRepository;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// WhatsApp de cada clínica como ponto de entrada (Plano_Desenvolvimento_WhatsApp_Por_Clinica_SGSM.docx,
// Fases 1 e 6). Conectar/desconectar: ADMIN_ESTABELECIMENTO da clínica (DW2) ou DESENVOLVEDOR; ver o
// status: também a secretária da clínica. A clínica de uma mensagem vem SEMPRE desta tabela
// (instância -> estabelecimento), nunca do texto da conversa.
@Service
@Transactional
public class WhatsappCanalService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappCanalService.class);

    private final WhatsappCanalRepository repository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final EstabelecimentoAcessoService estabelecimentoAcessoService;
    private final ContextoSeguranca contextoSeguranca;
    private final EvolutionInstanciaClient evolution;
    private final AlertaWhatsappService alerta;

    public WhatsappCanalService(WhatsappCanalRepository repository,
                                EstabelecimentoRepository estabelecimentoRepository,
                                EstabelecimentoAcessoService estabelecimentoAcessoService,
                                ContextoSeguranca contextoSeguranca,
                                EvolutionInstanciaClient evolution,
                                AlertaWhatsappService alerta) {
        this.repository = repository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.estabelecimentoAcessoService = estabelecimentoAcessoService;
        this.contextoSeguranca = contextoSeguranca;
        this.evolution = evolution;
        this.alerta = alerta;
    }

    // ── tela do admin ────────────────────────────────────────────────────────

    public WhatsappCanalResponse conectar(UUID estabelecimentoId) {
        exigirAdminDaClinica(estabelecimentoId);
        var existente = repository.findFirstByEstabelecimentoIdAndStatusNot(estabelecimentoId, WhatsappCanal.REMOVIDO);
        if (existente.isPresent()) {
            // já existe um número (DW3: 1 por clínica): devolve o estado atual com QR novo se ainda não conectou
            return consultarAtualizando(existente.get());
        }
        var canal = new WhatsappCanal();
        canal.setEstabelecimentoId(estabelecimentoId);
        canal.setInstancia(nomeDaInstancia(estabelecimentoId));
        canal.setStatus(WhatsappCanal.PENDENTE_QR);
        String usuario = contextoSeguranca.getUsuarioId();
        canal.setCriadoPor(paraUuid(usuario));
        String qr;
        try {
            qr = evolution.criar(canal.getInstancia());
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Não foi possível falar com o serviço de WhatsApp agora. Tente novamente.", e);
        }
        var salvo = repository.save(canal);
        log.info("Canal WhatsApp criado para o estabelecimento {} (instância {})", estabelecimentoId, salvo.getInstancia());
        return resposta(salvo, qr);
    }

    @Transactional
    public WhatsappCanalResponse consultar(UUID estabelecimentoId) {
        exigirEquipeDaClinica(estabelecimentoId);
        return repository.findFirstByEstabelecimentoIdAndStatusNot(estabelecimentoId, WhatsappCanal.REMOVIDO)
                .map(this::consultarAtualizando)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esta clínica ainda não conectou um WhatsApp."));
    }

    public void desconectar(UUID estabelecimentoId) {
        exigirAdminDaClinica(estabelecimentoId);
        var canal = repository.findFirstByEstabelecimentoIdAndStatusNot(estabelecimentoId, WhatsappCanal.REMOVIDO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esta clínica não tem WhatsApp conectado."));
        try {
            evolution.remover(canal.getInstancia());
        } catch (RestClientException e) {
            log.warn("Falha ao remover a instância {} na Evolution: {}", canal.getInstancia(), e.getMessage());
        }
        canal.setStatus(WhatsappCanal.REMOVIDO);
        repository.save(canal);
        log.info("Canal WhatsApp {} do estabelecimento {} removido", canal.getInstancia(), estabelecimentoId);
    }

    // ── uso interno (ms-whatsapp-bot, token de sistema) ──────────────────────

    @Transactional(readOnly = true)
    public WhatsappCanalInternoResponse resolver(String instancia) {
        var canal = repository.findByInstancia(instancia)
                .filter(c -> !WhatsappCanal.REMOVIDO.equals(c.getStatus()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Instância de WhatsApp desconhecida: " + instancia));
        return interno(canal);
    }

    @Transactional(readOnly = true)
    public List<WhatsappCanalInternoResponse> listarConectados() {
        return repository.findAllByStatus(WhatsappCanal.CONECTADO).stream().map(this::interno).toList();
    }

    // ── monitoramento (Fase 6) ───────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${sgsm.whatsapp.monitor-intervalo-ms:300000}",
               initialDelayString = "${sgsm.whatsapp.monitor-atraso-inicial-ms:60000}")
    public void monitorar() {
        for (var canal : repository.findAllByStatusNot(WhatsappCanal.REMOVIDO)) {
            String antes = canal.getStatus();
            try {
                atualizarEstado(canal);
            } catch (RestClientException e) {
                log.warn("Não foi possível consultar o WhatsApp {} (estabelecimento {}): {}",
                        canal.getInstancia(), canal.getEstabelecimentoId(), e.getMessage());
                continue;
            }
            if (!antes.equals(canal.getStatus())) {
                repository.save(canal);
                if (WhatsappCanal.DESCONECTADO.equals(canal.getStatus())) {
                    alerta.numeroDesconectado(canal);
                }
            }
        }
    }

    // ── suporte ──────────────────────────────────────────────────────────────

    private WhatsappCanalResponse consultarAtualizando(WhatsappCanal canal) {
        try {
            atualizarEstado(canal);
            repository.save(canal);
            String qr = WhatsappCanal.CONECTADO.equals(canal.getStatus()) ? null : evolution.qrCode(canal.getInstancia());
            return resposta(canal, qr);
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Não foi possível consultar o WhatsApp agora. Tente novamente.", e);
        }
    }

    private void atualizarEstado(WhatsappCanal canal) {
        String estado = evolution.estado(canal.getInstancia());
        switch (estado.toLowerCase(Locale.ROOT)) {
            case "open" -> {
                if (!WhatsappCanal.CONECTADO.equals(canal.getStatus())) {
                    canal.setConectadoEm(OffsetDateTime.now());
                }
                canal.setStatus(WhatsappCanal.CONECTADO);
                if (canal.getNumeroE164() == null) {
                    canal.setNumeroE164(evolution.numeroConectado(canal.getInstancia()));
                }
            }
            // nunca conectado continua aguardando o QR; já conectado e caiu vira DESCONECTADO
            default -> {
                if (WhatsappCanal.CONECTADO.equals(canal.getStatus())) {
                    canal.setStatus(WhatsappCanal.DESCONECTADO);
                }
            }
        }
    }

    private WhatsappCanalInternoResponse interno(WhatsappCanal canal) {
        String nome = estabelecimentoRepository.findById(canal.getEstabelecimentoId())
                .map(e -> e.getNome()).orElse(null);
        return new WhatsappCanalInternoResponse(canal.getInstancia(), canal.getEstabelecimentoId(), nome, canal.getStatus());
    }

    private static WhatsappCanalResponse resposta(WhatsappCanal c, String qr) {
        return new WhatsappCanalResponse(c.getId(), c.getEstabelecimentoId(), c.getProvedor(), c.getStatus(),
                c.getNumeroE164(), c.getConectadoEm(), qr);
    }

    private void exigirAdminDaClinica(UUID estabelecimentoId) {
        existeEstabelecimento(estabelecimentoId);
        if (contextoSeguranca.isDesenvolvedor()) return;
        if (!contextoSeguranca.isAdminEstabelecimento()) {
            throw new AcessoNegadoException("Só o administrador da clínica conecta ou desconecta o WhatsApp.");
        }
        estabelecimentoAcessoService.validarEstabelecimentoPermitido(estabelecimentoId);
    }

    private void exigirEquipeDaClinica(UUID estabelecimentoId) {
        existeEstabelecimento(estabelecimentoId);
        if (contextoSeguranca.isDesenvolvedor()) return;
        if (!contextoSeguranca.isAdminEstabelecimento() && !contextoSeguranca.isFuncionario()) {
            throw new AcessoNegadoException("Acesso negado ao WhatsApp da clínica.");
        }
        estabelecimentoAcessoService.validarEstabelecimentoPermitido(estabelecimentoId);
    }

    private void existeEstabelecimento(UUID estabelecimentoId) {
        if (!estabelecimentoRepository.existsById(estabelecimentoId)) {
            throw new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + estabelecimentoId);
        }
    }

    static String nomeDaInstancia(UUID estabelecimentoId) {
        return "sgsm-" + estabelecimentoId.toString().substring(0, 8) + "-"
                + UUID.randomUUID().toString().substring(0, 6);
    }

    private static UUID paraUuid(String valor) {
        try {
            return valor == null ? null : UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
