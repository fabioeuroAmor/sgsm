package br.com.sgsm.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// Gestão das instâncias (números) da Evolution API usadas no piloto do WhatsApp por clínica.
// A instância já nasce com o webhook apontando para o ms-whatsapp-bot e o header X-Webhook-Secret
// — sem ele o bot recusa a mensagem.
@Component
public class EvolutionInstanciaClient {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String webhookSecret;

    @Autowired
    public EvolutionInstanciaClient(@Value("${sgsm.whatsapp.evolution.base-url:http://localhost:8090}") String baseUrl,
                                    @Value("${sgsm.whatsapp.evolution.api-key:}") String apiKey,
                                    @Value("${sgsm.whatsapp.webhook-url:http://host.docker.internal:8083/webhook/evolution}") String webhookUrl,
                                    @Value("${sgsm.whatsapp.webhook-secret:}") String webhookSecret) {
        this(RestClient.builder().baseUrl(baseUrl).defaultHeader("apikey", apiKey), webhookUrl, webhookSecret);
    }

    EvolutionInstanciaClient(RestClient.Builder builder, String webhookUrl, String webhookSecret) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
    }

    // Cria a instância e devolve o QR code (base64 da imagem) para o admin ler no celular da clínica
    public String criar(String instancia) {
        Map<String, Object> resposta = restClient.post()
                .uri("/instance/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "instanceName", instancia,
                        "qrcode", true,
                        "integration", "WHATSAPP-BAILEYS",
                        "webhook", Map.of(
                                "url", webhookUrl,
                                "byEvents", false,
                                "base64", false,
                                "headers", Map.of("X-Webhook-Secret", webhookSecret),
                                "events", List.of("MESSAGES_UPSERT"))))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return qrDe(resposta == null ? null : resposta.get("qrcode"));
    }

    // Novo QR code enquanto o número não foi conectado (o QR do WhatsApp expira em segundos)
    public String qrCode(String instancia) {
        Map<String, Object> resposta = restClient.get()
                .uri("/instance/connect/{instancia}", instancia)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return qrDe(resposta);
    }

    // open | connecting | close
    public String estado(String instancia) {
        Map<String, Object> resposta = restClient.get()
                .uri("/instance/connectionState/{instancia}", instancia)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (resposta != null && resposta.get("instance") instanceof Map<?, ?> dados && dados.get("state") != null) {
            return dados.get("state").toString();
        }
        return "close";
    }

    // Número conectado em E.164 (+55...), a partir do ownerJid da instância
    public String numeroConectado(String instancia) {
        List<Map<String, Object>> instancias = restClient.get()
                .uri(uri -> uri.path("/instance/fetchInstances").queryParam("instanceName", instancia).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (instancias == null || instancias.isEmpty() || instancias.get(0).get("ownerJid") == null) {
            return null;
        }
        String jid = instancias.get(0).get("ownerJid").toString();
        String digitos = jid.contains("@") ? jid.substring(0, jid.indexOf('@')) : jid;
        return digitos.isBlank() ? null : "+" + digitos.replaceAll("\\D", "");
    }

    public void remover(String instancia) {
        try {
            restClient.delete().uri("/instance/logout/{instancia}", instancia).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            // instância nunca conectada não tem sessão para encerrar; segue para a exclusão
        }
        restClient.delete().uri("/instance/delete/{instancia}", instancia).retrieve().toBodilessEntity();
    }

    private static String qrDe(Object dados) {
        if (dados instanceof Map<?, ?> mapa && mapa.get("base64") != null) {
            return mapa.get("base64").toString();
        }
        return null;
    }
}
