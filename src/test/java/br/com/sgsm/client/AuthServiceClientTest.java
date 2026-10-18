package br.com.sgsm.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AuthServiceClientTest {

    @Test
    void registraPacienteComConfirmacaoDeEmail() {
        var builder = RestClient.builder().baseUrl("http://auth");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var client = new AuthServiceClient(builder);
        UUID id = UUID.randomUUID();

        servidor.expect(requestTo("http://auth/v1/api/auth/registrar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.tipoPerfil").value("PACIENTE"))
                .andExpect(jsonPath("$.referenciaId").value(id.toString()))
                .andExpect(jsonPath("$.confirmarEmail").value(true))
                .andExpect(jsonPath("$.email").value("a@b.com"))
                .andRespond(withStatus(HttpStatus.CREATED));
        servidor.expect(requestTo("http://auth/v1/api/auth/registrar"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        client.registrarPacienteComAtivacaoPorEmail("a@b.com", "senhaForte1", id);
        assertThatThrownBy(() -> client.registrarPacienteComAtivacaoPorEmail("a@b.com", "senhaForte1", id))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
        servidor.verify();
    }

    @Test
    void conviteDoMedicoRepassaOTokenDeQuemConvidaELeAResposta() {
        var builder = RestClient.builder().baseUrl("http://auth");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var client = new AuthServiceClient(builder);
        UUID id = UUID.randomUUID();

        servidor.expect(requestTo("http://auth/v1/api/auth/convite-medico"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt"))
                .andExpect(jsonPath("$.medicoId").value(id.toString()))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess("{\"email\":\"dr@a.com\",\"loginCriado\":true}",
                                org.springframework.http.MediaType.APPLICATION_JSON));

        var resposta = client.enviarConviteMedico(id, "Bearer jwt");

        assertThat(resposta.email()).isEqualTo("dr@a.com");
        assertThat(resposta.loginCriado()).isTrue();
        servidor.verify();
    }

    @Test
    void construtorDeProducaoUsaABaseUrlConfigurada() {
        assertThat(new AuthServiceClient("http://localhost:8081")).isNotNull();
    }
}
