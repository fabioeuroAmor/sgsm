package br.com.sgsm.whatsapp.client;

import br.com.sgsm.exception.IntegracaoException;
import br.com.sgsm.security.JwtService;
import br.com.sgsm.whatsapp.dto.IaChatRequest;
import br.com.sgsm.whatsapp.dto.IaChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

// Chama o RAG do sgsm-ia (POST /ia/chat) servico-a-servico. Toda a inteligencia (guardrails,
// Milvus, LLM, Camada 6) mora no sgsm-ia — este cliente so autentica e repassa a pergunta.
@Component
public class IaChatClient {

    private final RestTemplate restTemplate;
    private final JwtService jwtService;
    private final String iaBaseUrl;

    public IaChatClient(RestTemplate restTemplate,
                        JwtService jwtService,
                        @Value("${ia.base-url}") String iaBaseUrl) {
        this.restTemplate = restTemplate;
        this.jwtService = jwtService;
        this.iaBaseUrl = iaBaseUrl;
    }

    public String perguntar(String pergunta, String referenciaId, String perfil, String email) {
        String token = jwtService.gerarTokenServico(referenciaId, perfil, email);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var requisicao = new HttpEntity<>(new IaChatRequest(pergunta), headers);

        try {
            var resposta = restTemplate.postForEntity(iaBaseUrl + "/ia/chat", requisicao, IaChatResponse.class);
            IaChatResponse corpo = resposta.getBody();
            if (corpo == null || corpo.resposta() == null) {
                throw new IntegracaoException("sgsm-ia retornou resposta vazia", HttpStatus.BAD_GATEWAY, null);
            }
            return corpo.resposta();
        } catch (ResourceAccessException e) {
            throw new IntegracaoException("sgsm-ia indisponível ou demorou demais para responder",
                    HttpStatus.GATEWAY_TIMEOUT, e);
        } catch (RestClientException e) {
            throw new IntegracaoException("Falha ao consultar o sgsm-ia", HttpStatus.BAD_GATEWAY, e);
        }
    }
}
