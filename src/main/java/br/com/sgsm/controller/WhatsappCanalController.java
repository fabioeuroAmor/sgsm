package br.com.sgsm.controller;

import br.com.sgsm.dto.WhatsappCanalResponse;
import br.com.sgsm.service.WhatsappCanalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// WhatsApp da clínica (tela do admin). Escopo e papel validados no service.
@RestController
@RequestMapping("/v1/api/estabelecimentos/{id}/whatsapp")
public class WhatsappCanalController {

    private final WhatsappCanalService service;

    public WhatsappCanalController(WhatsappCanalService service) {
        this.service = service;
    }

    // UC - Conectar o número da clínica: devolve o QR code para ler no celular
    @PostMapping
    public ResponseEntity<WhatsappCanalResponse> conectar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.conectar(id));
    }

    // UC - Status do número (atualiza com a Evolution; QR novo enquanto não conectou)
    @GetMapping
    public ResponseEntity<WhatsappCanalResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    // UC - Desconectar e remover o número da clínica
    @DeleteMapping
    public ResponseEntity<Void> desconectar(@PathVariable UUID id) {
        service.desconectar(id);
        return ResponseEntity.noContent().build();
    }
}
