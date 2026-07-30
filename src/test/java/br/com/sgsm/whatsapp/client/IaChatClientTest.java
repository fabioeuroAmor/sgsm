package br.com.sgsm.whatsapp.client;

import br.com.sgsm.exception.IntegracaoException;
import br.com.sgsm.security.JwtService;
import br.com.sgsm.whatsapp.dto.IaChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IaChatClientTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private JwtService jwtService;

    private IaChatClient client;

    @BeforeEach
    void setUp() {
        client = new IaChatClient(restTemplate, jwtService, "http://localhost:8082");
        when(jwtService.gerarTokenServico(anyString(), anyString(), anyString())).thenReturn("token-servico");
    }

    @Test
    void devePerguntarERetornarRespostaDoRag() {
        when(restTemplate.postForEntity(eq("http://localhost:8082/ia/chat"), any(HttpEntity.class), eq(IaChatResponse.class)))
                .thenReturn(ResponseEntity.ok(new IaChatResponse("resposta do rag")));

        String resposta = client.perguntar("oi", "paciente-1", "PACIENTE", "p@sgsm.com.br");

        assertThat(resposta).isEqualTo("resposta do rag");
    }

    @Test
    void deveLancarIntegracaoException502QuandoRespostaVazia() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(IaChatResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertThatThrownBy(() -> client.perguntar("oi", "id", "PACIENTE", "e@sgsm.com.br"))
                .isInstanceOfSatisfying(IntegracaoException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void deveLancarIntegracaoException502QuandoSgsmIaRetornaErro() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(IaChatResponse.class)))
                .thenThrow(new RestClientException("500 do sgsm-ia"));

        assertThatThrownBy(() -> client.perguntar("oi", "id", "PACIENTE", "e@sgsm.com.br"))
                .isInstanceOfSatisfying(IntegracaoException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void deveLancarIntegracaoException504QuandoSgsmIaIndisponivel() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(IaChatResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> client.perguntar("oi", "id", "PACIENTE", "e@sgsm.com.br"))
                .isInstanceOfSatisfying(IntegracaoException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }
}
