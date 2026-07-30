package br.com.sgsm.whatsapp.service;

import br.com.sgsm.exception.IntegracaoException;
import br.com.sgsm.whatsapp.client.IaChatClient;
import br.com.sgsm.whatsapp.client.WhatsAppService;
import br.com.sgsm.whatsapp.dto.received.WhatsAppWebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Orquestra o fluxo inbound do canal WhatsApp (secao 9.2 do desenho de solucao):
// rate-limit -> identificacao (paciente x lead) -> RAG (so para paciente identificado) ->
// resposta -> historico em crm.contato_paciente. O sgsm-ia nao conhece esse canal: so
// recebe pergunta + identidade via /ia/chat e devolve texto ja passado pelos guardrails.
@Service
public class WhatsAppOrquestradorService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppOrquestradorService.class);

    private static final String MSG_AGUARDO =
            "Você está enviando muitas mensagens em pouco tempo. Aguarde um minuto e tente novamente.";
    private static final String MSG_INDISPONIVEL =
            "No momento não consigo responder sua pergunta. Tente novamente em alguns minutos.";
    private static final String MSG_VISITANTE =
            "Olá! Sou o assistente virtual do SGSM. Por enquanto posso te ajudar só com informações "
                    + "gerais (serviços, horários, endereços). Em breve alguém da nossa equipe vai falar com você.";

    private final RateLimitService rateLimitService;
    private final PacienteIdentificacaoService pacienteIdentificacaoService;
    private final LeadService leadService;
    private final ContatoPacienteService contatoPacienteService;
    private final IaChatClient iaChatClient;
    private final WhatsAppService whatsAppService;

    public WhatsAppOrquestradorService(RateLimitService rateLimitService,
                                       PacienteIdentificacaoService pacienteIdentificacaoService,
                                       LeadService leadService,
                                       ContatoPacienteService contatoPacienteService,
                                       IaChatClient iaChatClient,
                                       WhatsAppService whatsAppService) {
        this.rateLimitService = rateLimitService;
        this.pacienteIdentificacaoService = pacienteIdentificacaoService;
        this.leadService = leadService;
        this.contatoPacienteService = contatoPacienteService;
        this.iaChatClient = iaChatClient;
        this.whatsAppService = whatsAppService;
    }

    public void processarMensagemRecebida(WhatsAppWebhookRequest payload) {
        if (payload == null || !payload.ehMensagemRecebidaValida()) {
            return;
        }

        String telefone = payload.telefoneRemetente();
        String texto = payload.texto();

        if (rateLimitService.excedeuLimite(telefone)) {
            log.warn("Rate limit excedido para telefone {}", telefone);
            whatsAppService.enviarTexto(telefone, MSG_AGUARDO);
            return;
        }

        Optional<String> pacienteId = pacienteIdentificacaoService.identificarPacienteId(telefone);
        if (pacienteId.isEmpty()) {
            leadService.criarOuObterExistente(telefone, nomeOuPadrao(payload));
            whatsAppService.enviarTexto(telefone, MSG_VISITANTE);
            return;
        }

        String id = pacienteId.get();
        contatoPacienteService.registrarEntrada(id, texto);

        String resposta;
        try {
            // referenciaId=id + perfil=PACIENTE -> Camada 6 do sgsm-ia restringe o RAG
            // aos proprios dados desse paciente (ver EscopoAcessoResolver no sgsm-ia).
            resposta = iaChatClient.perguntar(texto, id, "PACIENTE", null);
        } catch (IntegracaoException e) {
            log.error("sgsm-ia indisponível ao responder WhatsApp do paciente {}: {}", id, e.getMessage());
            resposta = MSG_INDISPONIVEL;
        }

        whatsAppService.enviarTexto(telefone, resposta);
        contatoPacienteService.registrarSaida(id, resposta);
    }

    private String nomeOuPadrao(WhatsAppWebhookRequest payload) {
        String nome = payload.nomeExibicao();
        return (nome == null || nome.isBlank()) ? "Contato WhatsApp" : nome;
    }
}
