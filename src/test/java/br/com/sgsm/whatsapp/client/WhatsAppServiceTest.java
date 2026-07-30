package br.com.sgsm.whatsapp.client;

import br.com.sgsm.exception.IntegracaoException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppService(restTemplate, "http://localhost:8084", "chave-api", "sgsm");
    }

    @Test
    void deveEnviarTextoParaEndpointCorretoDaInstancia() {
        when(restTemplate.postForEntity(eq("http://localhost:8084/message/sendText/sgsm"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        service.enviarTexto("5561999998888", "olá");

        verify(restTemplate).postForEntity(eq("http://localhost:8084/message/sendText/sgsm"),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void deveLancarIntegracaoException504QuandoEvolutionApiIndisponivel() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> service.enviarTexto("5561999998888", "olá"))
                .isInstanceOfSatisfying(IntegracaoException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void deveLancarIntegracaoException502QuandoEvolutionApiRetornaErro() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("500"));

        assertThatThrownBy(() -> service.enviarTexto("5561999998888", "olá"))
                .isInstanceOfSatisfying(IntegracaoException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
