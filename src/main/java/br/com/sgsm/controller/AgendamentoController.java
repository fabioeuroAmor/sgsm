package br.com.sgsm.controller;

import br.com.sgsm.domain.StatusAgendamento;
import br.com.sgsm.dto.*;
import br.com.sgsm.service.AgendamentoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/agendamentos")
public class AgendamentoController {

    private final AgendamentoService service;

    public AgendamentoController(AgendamentoService service) {
        this.service = service;
    }

    // UC - Listar estabelecimentos onde o médico atende
    @GetMapping("/medico/{medicoId}/estabelecimentos")
    public ResponseEntity<List<EstabelecimentoResponse>> listarEstabelecimentosPorMedico(
            @PathVariable UUID medicoId) {
        return ResponseEntity.ok(service.listarEstabelecimentosPorMedico(medicoId));
    }

    // UC - Listar slots disponíveis
    @GetMapping("/slots")
    public ResponseEntity<List<SlotDisponivelResponse>> listarSlots(
            @RequestParam UUID medicoId,
            @RequestParam(required = false) UUID estabelecimentoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(service.listarSlotsDisponiveis(medicoId, estabelecimentoId, data));
    }

    // UC - Listar dias do mês com pelo menos um slot disponível (para destacar no calendário)
    @GetMapping("/dias-disponiveis")
    public ResponseEntity<List<LocalDate>> listarDiasComDisponibilidade(
            @RequestParam UUID medicoId,
            @RequestParam(required = false) UUID estabelecimentoId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return ResponseEntity.ok(service.listarDiasComDisponibilidade(medicoId, estabelecimentoId, mes));
    }

    // UC - Cadastrar agendamento
    @PostMapping
    public ResponseEntity<AgendamentoResponse> cadastrar(@RequestBody CadastrarAgendamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request));
    }

    // UC - Consultar agendamento
    @GetMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(service.consultar(id));
    }

    // UC - Listar agendamentos (filtros opcionais)
    @GetMapping
    public ResponseEntity<List<AgendamentoResponse>> listar(
            @RequestParam(required = false) UUID pacienteId,
            @RequestParam(required = false) StatusAgendamento status,
            @RequestParam(required = false) UUID medicoId) {
        return ResponseEntity.ok(service.listar(pacienteId, status, medicoId));
    }

    // UC - Atualizar status do agendamento
    @PatchMapping("/{id}/status")
    public ResponseEntity<AgendamentoResponse> atualizarStatus(
            @PathVariable UUID id,
            @RequestBody AtualizarStatusAgendamentoRequest request) {
        return ResponseEntity.ok(service.atualizarStatus(id, request.status(), request.localizacaoMedico()));
    }

    // UC - Cancelar agendamento
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(
            @PathVariable UUID id,
            @RequestBody CancelarAgendamentoRequest request) {
        service.cancelar(id, request);
        return ResponseEntity.noContent().build();
    }
}
