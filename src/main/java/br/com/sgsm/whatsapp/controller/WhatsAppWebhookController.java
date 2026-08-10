package br.com.sgsm.whatsapp.controller;

import br.com.sgsm.whatsapp.dto.received.WhatsAppWebhookRequest;
import br.com.sgsm.whatsapp.service.WhatsAppOrquestradorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Rota publica no SecurityConfig (nao existe JWT nesse canal), protegida por token proprio
// no header — sem token valido: 401. Ver secao 9.1 do desenho de solucao.
@RestController
@RequestMapping("/v1/api/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String HEADER_TOKEN = "X-Webhook-Token";

    private final WhatsAppOrquestradorService orquestradorService;
    private final String webhookToken;

    public WhatsAppWebhookController(WhatsAppOrquestradorService orquestradorService,
                                     @Value("${whatsapp.webhook-token}") String webhookToken) {
        this.orquestradorService = orquestradorService;
        this.webhookToken = webhookToken;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = HEADER_TOKEN, required = false) String token,
                                        @RequestBody WhatsAppWebhookRequest payload) {
        if (webhookToken == null || webhookToken.isBlank() || !tokenValido(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            orquestradorService.processarMensagemRecebida(payload);
        } catch (Exception e) {
            // O webhook nunca devolve 500 pra Evolution API (secao 9.4) — se devolvesse, ela
            // reenviaria o mesmo evento em loop. A falha fica logada para investigacao.
            log.error("Falha ao processar mensagem de WhatsApp: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    // MessageDigest.isEqual e constant-time: evita vazar, por diferenca de tempo de resposta,
    // em qual posicao o token recebido diverge do webhookToken configurado.
    private boolean tokenValido(String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                webhookToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
