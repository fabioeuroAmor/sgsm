package br.com.sgsm.controller;

import br.com.sgsm.dto.AutoCadastroPacienteRequest;
import br.com.sgsm.dto.AutoCadastroPacienteResponse;
import br.com.sgsm.service.AutoCadastroPacienteService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoCadastroPacienteControllerTest {

    @Mock private AutoCadastroPacienteService service;
    @Mock private HttpServletRequest http;

    @Test
    void cadastroPublicoRepassaOIpDoClienteEDevolve201() {
        var pedido = new AutoCadastroPacienteRequest("Maria", "52998224725", LocalDate.of(1990, 1, 1),
                "a@b.com", null, true, "senhaForte1");
        var resposta = new AutoCadastroPacienteResponse("a@b.com", true);
        when(http.getRemoteAddr()).thenReturn("10.0.0.1");
        when(service.cadastrar(pedido, "10.0.0.1")).thenReturn(resposta);

        var r = new AutoCadastroPacienteController(service).cadastrar(pedido, http);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).isEqualTo(resposta);
    }
}
