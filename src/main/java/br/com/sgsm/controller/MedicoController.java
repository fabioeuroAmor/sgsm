package br.com.sgsm.controller;

import br.com.sgsm.dto.AtualizarMedicoRequest;
import br.com.sgsm.dto.CadastrarMedicoRequest;
import br.com.sgsm.dto.MedicoResponse;
import br.com.sgsm.service.MedicoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/medicos")
public class MedicoController {

    private final MedicoService service;

    public MedicoController(MedicoService service) {
        this.service = service;
    }

    // UC - Cadastrar médico
    @PostMapping
    public ResponseEntity<MedicoResponse> cadastrar(@RequestBody CadastrarMedicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request));
    }

    // UC - Consultar médico
    @GetMapping("/{id}")
    public ResponseEntity<MedicoResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    // UC - Atualizar médico
    @PutMapping("/{id}")
    public ResponseEntity<MedicoResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody AtualizarMedicoRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    // UC - Remover (inativar) médico
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    // UC - Listar médicos
    @GetMapping
    public ResponseEntity<List<MedicoResponse>> listar(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String especialidade) {
        return ResponseEntity.ok(service.listar(ativo, especialidade));
    }
}
