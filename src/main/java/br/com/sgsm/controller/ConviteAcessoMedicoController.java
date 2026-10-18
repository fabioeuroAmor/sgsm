package br.com.sgsm.controller;

import br.com.sgsm.dto.ConviteAcessoMedicoResponse;
import br.com.sgsm.service.ConviteAcessoMedicoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// POST /medicos/** já é restrito a FUNCIONARIO, DESENVOLVEDOR e ADMIN_ESTABELECIMENTO (SecurityConfig)
@RestController
@RequestMapping("/v1/api/medicos")
public class ConviteAcessoMedicoController {

    private final ConviteAcessoMedicoService service;

    public ConviteAcessoMedicoController(ConviteAcessoMedicoService service) {
        this.service = service;
    }

    // UC - Enviar ao médico o convite para criar a própria senha de acesso
    @PostMapping("/{id}/convite-acesso")
    public ResponseEntity<ConviteAcessoMedicoResponse> convidar(@PathVariable UUID id,
                                                                @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(service.convidar(id, authorization));
    }
}
