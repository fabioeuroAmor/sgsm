package br.com.sgsm.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvolutionInstanciaClientTest {

    private MockRestServiceServer servidor;
    private EvolutionInstanciaClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://evo");
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new EvolutionInstanciaClient(builder, "http://bot/webhook/evolution", "segredo");
    }

    @Test
    void criaAInstanciaComOWebhookDoBotEOSegredo() {
        servidor.expect(requestTo("http://evo/instance/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.instanceName").value("sgsm-abc"))
                .andExpect(jsonPath("$.qrcode").value(true))
                .andExpect(jsonPath("$.webhook.url").value("http://bot/webhook/evolution"))
                .andExpect(jsonPath("$.webhook.headers.X-Webhook-Secret").value("segredo"))
                .andExpect(jsonPath("$.webhook.events[0]").value("MESSAGES_UPSERT"))
                .andRespond(withSuccess("{\"qrcode\":{\"base64\":\"data:image/png;base64,QR\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.criar("sgsm-abc")).isEqualTo("data:image/png;base64,QR");
        servidor.verify();
    }

    @Test
    void qrEstadoENumeroConectado() {
        servidor.expect(requestTo("http://evo/instance/connect/sgsm-abc"))
                .andRespond(withSuccess("{\"base64\":\"QR2\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://evo/instance/connectionState/sgsm-abc"))
                .andRespond(withSuccess("{\"instance\":{\"state\":\"open\"}}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://evo/instance/connectionState/sgsm-abc"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://evo/instance/fetchInstances?instanceName=sgsm-abc"))
                .andRespond(withSuccess("[{\"ownerJid\":\"5561999990000@s.whatsapp.net\"}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://evo/instance/fetchInstances?instanceName=sgsm-abc"))
                .andRespond(withSuccess("[{\"ownerJid\":null}]", MediaType.APPLICATION_JSON));

        assertThat(client.qrCode("sgsm-abc")).isEqualTo("QR2");
        assertThat(client.estado("sgsm-abc")).isEqualTo("open");
        assertThat(client.estado("sgsm-abc")).isEqualTo("close");
        assertThat(client.numeroConectado("sgsm-abc")).isEqualTo("+5561999990000");
        assertThat(client.numeroConectado("sgsm-abc")).isNull();
        servidor.verify();
    }

    @Test
    void removerFazLogoutEExcluiMesmoSemSessao() {
        servidor.expect(requestTo("http://evo/instance/logout/sgsm-abc"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        servidor.expect(requestTo("http://evo/instance/delete/sgsm-abc"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        client.remover("sgsm-abc");
        servidor.verify();
    }

    @Test
    void construtorDeProducao() {
        assertThat(new EvolutionInstanciaClient("http://localhost:8090", "chave", "http://bot", "s")).isNotNull();
    }
}
