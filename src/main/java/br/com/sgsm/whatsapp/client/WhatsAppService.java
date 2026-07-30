package br.com.sgsm.whatsapp.client;

import br.com.sgsm.exception.IntegracaoException;
import br.com.sgsm.whatsapp.dto.sent.WhatsAppMensagemSent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

// Envio de mensagens via Evolution API (gateway WhatsApp — ver secao 9 do desenho de solucao).
@Component
public class WhatsAppService {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String instance;

    public WhatsAppService(RestTemplate restTemplate,
                           @Value("${evolution.api.url}") String apiUrl,
                           @Value("${evolution.api.key}") String apiKey,
                           @Value("${evolution.api.instance}") String instance) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.instance = instance;
    }

    public void enviarTexto(String numero, String texto) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var requisicao = new HttpEntity<>(new WhatsAppMensagemSent(numero, texto), headers);

        try {
            restTemplate.postForEntity(apiUrl + "/message/sendText/" + instance, requisicao, String.class);
        } catch (ResourceAccessException e) {
            throw new IntegracaoException("Evolution API indisponível", HttpStatus.GATEWAY_TIMEOUT, e);
        } catch (RestClientException e) {
            throw new IntegracaoException("Falha ao enviar mensagem via Evolution API", HttpStatus.BAD_GATEWAY, e);
        }
    }
}
