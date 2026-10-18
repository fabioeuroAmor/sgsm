package br.com.sgsm.controller;

import br.com.sgsm.dto.ConviteAcessoMedicoResponse;
import br.com.sgsm.service.ConviteAcessoMedicoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConviteAcessoMedicoControllerTest {

    @Mock private ConviteAcessoMedicoService service;

    @Test
    void conviteRepassaOTokenDeQuemConvida() {
        var id = UUID.randomUUID();
        var resposta = new ConviteAcessoMedicoResponse("dr@a.com", true);
        when(service.convidar(id, "Bearer jwt")).thenReturn(resposta);

        var r = new ConviteAcessoMedicoController(service).convidar(id, "Bearer jwt");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(resposta);
    }
}
