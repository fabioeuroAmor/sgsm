package br.com.sgsm.controller;

import br.com.sgsm.dto.*;
import br.com.sgsm.service.AdministradorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/administradores")
public class AdministradorController {

    private final AdministradorService service;

    public AdministradorController(AdministradorService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AdministradorResponse> cadastrar(@RequestBody CadastrarAdministradorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdministradorResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    @GetMapping
    public ResponseEntity<List<AdministradorResponse>> listar(
            @RequestParam(required = false) Boolean ativo) {
        return ResponseEntity.ok(service.listar(ativo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdministradorResponse> atualizar(@PathVariable UUID id,
                                                             @RequestBody AtualizarAdministradorRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<AdministradorResponse> reativar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reativar(id));
    }

    @GetMapping("/{id}/estabelecimentos")
    public ResponseEntity<List<EstabelecimentoResponse>> listarEstabelecimentos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarEstabelecimentos(id));
    }

    @PutMapping("/{id}/estabelecimentos")
    public ResponseEntity<Void> vincularEstabelecimentos(@PathVariable UUID id,
                                                           @RequestBody AssociarEstabelecimentosRequest request) {
        service.vincularEstabelecimentos(id, request);
        return ResponseEntity.noContent().build();
    }
}
