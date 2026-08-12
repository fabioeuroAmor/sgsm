package br.com.sgsm.controller;

import br.com.sgsm.dto.*;
import br.com.sgsm.service.EstabelecimentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/estabelecimentos")
public class EstabelecimentoController {

    private final EstabelecimentoService service;

    public EstabelecimentoController(EstabelecimentoService service) {
        this.service = service;
    }

    // UC - Cadastrar estabelecimento
    @PostMapping
    public ResponseEntity<EstabelecimentoResponse> cadastrar(@Valid @RequestBody CadastrarEstabelecimentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request));
    }

    // UC - Consultar estabelecimento
    @GetMapping("/{id}")
    public ResponseEntity<EstabelecimentoResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    // UC - Atualizar estabelecimento
    @PutMapping("/{id}")
    public ResponseEntity<EstabelecimentoResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody AtualizarEstabelecimentoRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    // UC - Remover (inativar) estabelecimento
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    // UC - Listar estabelecimentos
    @GetMapping
    public ResponseEntity<List<EstabelecimentoResponse>> listar(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) UUID medicoId) {
        return ResponseEntity.ok(service.listar(ativo, uf, cidade, medicoId));
    }

    // UC - Listar médicos vinculados ao estabelecimento
    @GetMapping("/{id}/medicos")
    public ResponseEntity<List<MedicoResponse>> listarMedicos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarMedicos(id));
    }

    // UC - Associar médicos ao estabelecimento
    @PutMapping("/{id}/medicos")
    public ResponseEntity<Void> associarMedicos(
            @PathVariable UUID id,
            @RequestBody AssociarMedicosRequest request) {
        service.associarMedicos(id, request);
        return ResponseEntity.noContent().build();
    }
}
