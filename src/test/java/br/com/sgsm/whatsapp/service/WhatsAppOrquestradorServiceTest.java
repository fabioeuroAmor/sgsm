package br.com.sgsm.whatsapp.service;

import br.com.sgsm.exception.IntegracaoException;
import br.com.sgsm.whatsapp.client.IaChatClient;
import br.com.sgsm.whatsapp.client.WhatsAppService;
import br.com.sgsm.whatsapp.dto.received.WhatsAppWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppOrquestradorServiceTest {

    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private PacienteIdentificacaoService pacienteIdentificacaoService;
    @Mock
    private LeadService leadService;
    @Mock
    private ContatoPacienteService contatoPacienteService;
    @Mock
    private IaChatClient iaChatClient;
    @Mock
    private WhatsAppService whatsAppService;

    private WhatsAppOrquestradorService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppOrquestradorService(rateLimitService, pacienteIdentificacaoService,
                leadService, contatoPacienteService, iaChatClient, whatsAppService);
    }

    private WhatsAppWebhookRequest mensagemRecebida(String telefone, String texto, String pushName) {
        var key = new WhatsAppWebhookRequest.Key(telefone + "@s.whatsapp.net", false, "msg-1");
        var message = new WhatsAppWebhookRequest.Mensagem(texto);
        var data = new WhatsAppWebhookRequest.Dados(key, pushName, message);
        return new WhatsAppWebhookRequest("messages.upsert", "sgsm", data);
    }

    @Test
    void ignoraMensagemQueNaoEhRecebimentoValido() {
        var ecoDaPropriaInstancia = new WhatsAppWebhookRequest("messages.upsert", "sgsm",
                new WhatsAppWebhookRequest.Dados(
                        new WhatsAppWebhookRequest.Key("5561999998888@s.whatsapp.net", true, "msg-1"),
                        "SGSM", new WhatsAppWebhookRequest.Mensagem("oi")));

        service.processarMensagemRecebida(ecoDaPropriaInstancia);

        verifyNoInteractions(rateLimitService, pacienteIdentificacaoService, leadService,
                contatoPacienteService, iaChatClient, whatsAppService);
    }

    @Test
    void respondeMensagemDeAguardoQuandoExcedeRateLimit() {
        when(rateLimitService.excedeuLimite("5561999998888")).thenReturn(true);

        service.processarMensagemRecebida(mensagemRecebida("5561999998888", "oi", "Fulano"));

        verify(whatsAppService).enviarTexto(eq("5561999998888"), contains("muitas mensagens"));
        verifyNoInteractions(pacienteIdentificacaoService, leadService, contatoPacienteService, iaChatClient);
    }

    @Test
    void telefoneNaoCadastradoViraLeadERecebeRespostaInstitucional() {
        when(rateLimitService.excedeuLimite(anyString())).thenReturn(false);
        when(pacienteIdentificacaoService.identificarPacienteId("5561999998888")).thenReturn(Optional.empty());

        service.processarMensagemRecebida(mensagemRecebida("5561999998888", "Quanto custa a consulta?", "Visitante"));

        verify(leadService).criarOuObterExistente("5561999998888", "Visitante");
        verify(whatsAppService).enviarTexto(eq("5561999998888"), contains("assistente virtual"));
        verifyNoInteractions(iaChatClient, contatoPacienteService);
    }

    @Test
    void usaNomePadraoQuandoPushNameAusenteAoCriarLead() {
        when(rateLimitService.excedeuLimite(anyString())).thenReturn(false);
        when(pacienteIdentificacaoService.identificarPacienteId(anyString())).thenReturn(Optional.empty());

        service.processarMensagemRecebida(mensagemRecebida("5561999998888", "oi", null));

        verify(leadService).criarOuObterExistente("5561999998888", "Contato WhatsApp");
    }

    @Test
    void pacienteIdentificadoConsultaRagEEnviaResposta() {
        when(rateLimitService.excedeuLimite(anyString())).thenReturn(false);
        when(pacienteIdentificacaoService.identificarPacienteId("5561999998888"))
                .thenReturn(Optional.of("paciente-1"));
        when(iaChatClient.perguntar("Qual meu histórico?", "paciente-1", "PACIENTE", null))
                .thenReturn("Você tem 3 consultas concluídas.");

        service.processarMensagemRecebida(mensagemRecebida("5561999998888", "Qual meu histórico?", "Ingrid"));

        verify(contatoPacienteService).registrarEntrada("paciente-1", "Qual meu histórico?");
        verify(whatsAppService).enviarTexto("5561999998888", "Você tem 3 consultas concluídas.");
        verify(contatoPacienteService).registrarSaida("paciente-1", "Você tem 3 consultas concluídas.");
        verifyNoInteractions(leadService);
    }

    @Test
    void respondeMensagemDeIndisponibilidadeQuandoSgsmIaFalha() {
        when(rateLimitService.excedeuLimite(anyString())).thenReturn(false);
        when(pacienteIdentificacaoService.identificarPacienteId("5561999998888"))
                .thenReturn(Optional.of("paciente-1"));
        when(iaChatClient.perguntar(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IntegracaoException("sgsm-ia fora do ar", HttpStatus.GATEWAY_TIMEOUT, null));

        service.processarMensagemRecebida(mensagemRecebida("5561999998888", "oi", "Ingrid"));

        verify(whatsAppService).enviarTexto(eq("5561999998888"), contains("não consigo responder"));
        verify(contatoPacienteService).registrarSaida(eq("paciente-1"), contains("não consigo responder"));
    }
}
