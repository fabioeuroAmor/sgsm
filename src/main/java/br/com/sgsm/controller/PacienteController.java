package br.com.sgsm.controller;

import br.com.sgsm.dto.AtualizarPacienteRequest;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.dto.PacienteResponse;
import br.com.sgsm.service.PacienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/pacientes")
public class PacienteController {

    private final PacienteService service;

    public PacienteController(PacienteService service) {
        this.service = service;
    }

    // UC - Cadastrar paciente
    @PostMapping
    public ResponseEntity<PacienteResponse> cadastrar(@RequestBody CadastrarPacienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request));
    }

    // UC - Consultar paciente
    @GetMapping("/{id}")
    public ResponseEntity<PacienteResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    // UC - Atualizar paciente
    @PutMapping("/{id}")
    public ResponseEntity<PacienteResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody AtualizarPacienteRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    // UC - Remover (inativar) paciente
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    // UC - Listar pacientes
    @GetMapping
    public ResponseEntity<List<PacienteResponse>> listar(
            @RequestParam(required = false) Boolean ativo) {
        return ResponseEntity.ok(service.listar(ativo));
    }
}
