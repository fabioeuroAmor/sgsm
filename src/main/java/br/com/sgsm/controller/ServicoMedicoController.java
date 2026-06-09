package br.com.sgsm.controller;

import br.com.sgsm.dto.AtualizarServicoMedicoRequest;
import br.com.sgsm.dto.CadastrarServicoMedicoRequest;
import br.com.sgsm.dto.ServicoMedicoResponse;
import br.com.sgsm.service.ServicoMedicoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/servicos-medicos")
public class ServicoMedicoController {

    private final ServicoMedicoService service;

    public ServicoMedicoController(ServicoMedicoService service) {
        this.service = service;
    }

    // UC01 - Cadastrar serviço médico
    @PostMapping
    public ResponseEntity<ServicoMedicoResponse> cadastrar(@RequestBody CadastrarServicoMedicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request));
    }

    // UC02 - Consultar serviço médico
    @GetMapping("/{id}")
    public ResponseEntity<ServicoMedicoResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    // UC03 - Atualizar serviço médico
    @PutMapping("/{id}")
    public ResponseEntity<ServicoMedicoResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody AtualizarServicoMedicoRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    // UC04 - Remover (inativar) serviço médico
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    // UC05 - Listar serviços médicos
    @GetMapping
    public ResponseEntity<List<ServicoMedicoResponse>> listar(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) UUID medicoId) {
        return ResponseEntity.ok(service.listar(ativo, medicoId));
    }
}
