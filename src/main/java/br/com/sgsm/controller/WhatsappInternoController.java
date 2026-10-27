package br.com.sgsm.controller;

import br.com.sgsm.dto.ConsentimentoRequest;
import br.com.sgsm.dto.VinculoPacienteClinicaRequest;
import br.com.sgsm.dto.WhatsappCanalInternoResponse;
import br.com.sgsm.service.PacienteClinicaService;
import br.com.sgsm.service.WhatsappCanalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Endpoints do ms-whatsapp-bot (token de sistema DESENVOLVEDOR, ver SecurityConfig /v1/api/interno/**)
@RestController
@RequestMapping("/v1/api/interno/whatsapp")
public class WhatsappInternoController {

    private final WhatsappCanalService canalService;
    private final PacienteClinicaService pacienteClinicaService;

    public WhatsappInternoController(WhatsappCanalService canalService, PacienteClinicaService pacienteClinicaService) {
        this.canalService = canalService;
        this.pacienteClinicaService = pacienteClinicaService;
    }

    // De qual clínica é a instância que recebeu a mensagem
    @GetMapping("/canais/{instancia}")
    public ResponseEntity<WhatsappCanalInternoResponse> canal(@PathVariable String instancia) {
        return ResponseEntity.ok(canalService.resolver(instancia));
    }

    // Números conectados (agenda diária por clínica)
    @GetMapping("/canais")
    public ResponseEntity<List<WhatsappCanalInternoResponse>> conectados() {
        return ResponseEntity.ok(canalService.listarConectados());
    }

    @PostMapping("/vinculos")
    public ResponseEntity<Void> vincular(@RequestBody VinculoPacienteClinicaRequest request) {
        pacienteClinicaService.vincular(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/consentimentos")
    public ResponseEntity<Void> consentir(@RequestBody ConsentimentoRequest request) {
        pacienteClinicaService.registrarConsentimento(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/consentimentos")
    public ResponseEntity<Map<String, Boolean>> consentimentoVigente(@RequestParam String telefone,
                                                                     @RequestParam UUID estabelecimentoId,
                                                                     @RequestParam(defaultValue = "WHATSAPP") String canal) {
        return ResponseEntity.ok(Map.of("vigente",
                pacienteClinicaService.consentimentoVigente(telefone, estabelecimentoId, canal)));
    }
}
