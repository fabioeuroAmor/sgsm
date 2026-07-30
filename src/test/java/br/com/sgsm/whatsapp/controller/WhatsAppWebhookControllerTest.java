package br.com.sgsm.whatsapp.controller;

import br.com.sgsm.whatsapp.dto.received.WhatsAppWebhookRequest;
import br.com.sgsm.whatsapp.service.WhatsAppOrquestradorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock
    private WhatsAppOrquestradorService orquestradorService;

    private WhatsAppWebhookController controller;

    private final WhatsAppWebhookRequest payload = new WhatsAppWebhookRequest("messages.upsert", "sgsm", null);

    @BeforeEach
    void setUp() {
        controller = new WhatsAppWebhookController(orquestradorService, "token-secreto");
    }

    @Test
    void deveRetornar200EProcessarQuandoTokenValido() {
        var response = controller.webhook("token-secreto", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(orquestradorService).processarMensagemRecebida(payload);
    }

    @Test
    void deveRetornar401QuandoTokenInvalido() {
        var response = controller.webhook("token-errado", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(orquestradorService);
    }

    @Test
    void deveRetornar401QuandoTokenAusente() {
        var response = controller.webhook(null, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(orquestradorService);
    }

    @Test
    void nuncaRetorna500MesmoQuandoOrquestradorFalha() {
        doThrow(new RuntimeException("falha inesperada")).when(orquestradorService).processarMensagemRecebida(payload);

        var response = controller.webhook("token-secreto", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
