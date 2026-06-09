package br.com.sgsm.controller;

import br.com.sgsm.dto.AgendaMedicoResponse;
import br.com.sgsm.dto.CadastrarAgendaMedicoRequest;
import br.com.sgsm.service.AgendaMedicoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/agenda-medico")
public class AgendaMedicoController {

    private final AgendaMedicoService service;

    public AgendaMedicoController(AgendaMedicoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AgendaMedicoResponse>> listar(@RequestParam UUID medicoId) {
        return ResponseEntity.ok(service.listar(medicoId));
    }

    @PostMapping
    public ResponseEntity<AgendaMedicoResponse> cadastrar(@RequestBody CadastrarAgendaMedicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }
}
