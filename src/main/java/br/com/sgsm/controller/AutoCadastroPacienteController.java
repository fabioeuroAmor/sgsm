package br.com.sgsm.controller;

import br.com.sgsm.dto.AutoCadastroPacienteRequest;
import br.com.sgsm.dto.AutoCadastroPacienteResponse;
import br.com.sgsm.service.AutoCadastroPacienteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Único endpoint público de escrita do sgsm (permitAll em SecurityConfig). O IP vem de
// getRemoteAddr(): atrás de proxy, configurar server.forward-headers-strategy para usar o IP real
@RestController
@RequestMapping("/v1/api/publico/pacientes")
public class AutoCadastroPacienteController {

    private final AutoCadastroPacienteService service;

    public AutoCadastroPacienteController(AutoCadastroPacienteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AutoCadastroPacienteResponse> cadastrar(@RequestBody AutoCadastroPacienteRequest request,
                                                                  HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(request, http.getRemoteAddr()));
    }
}
