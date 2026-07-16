package br.com.sgsm.controller;

import br.com.sgsm.dto.AgendaMedicoResponse;
import br.com.sgsm.dto.CadastrarAgendaMedicoRequest;
import br.com.sgsm.service.AgendaMedicoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgendaMedicoController é um controller "fino": apenas delega para o service e traduz o
 * retorno em ResponseEntity com o status HTTP adequado. Os testes verificam essa delegação
 * e o status HTTP, sem subir contexto Spring (MockMvc), mantendo os testes rápidos.
 */
@ExtendWith(MockitoExtension.class)
class AgendaMedicoControllerTest {

    @Mock
    private AgendaMedicoService service;

    private AgendaMedicoController controller;

    @BeforeEach
    void setUp() {
        controller = new AgendaMedicoController(service);
    }

    @Test
    void deveRetornar200ComListaAoListar() {
        UUID medicoId = UUID.randomUUID();
        var resposta = new AgendaMedicoResponse();
        when(service.listar(medicoId)).thenReturn(List.of(resposta));

        var response = controller.listar(medicoId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }

    @Test
    void deveRetornar201AoCadastrar() {
        var request = new CadastrarAgendaMedicoRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, "08:00", "12:00", 30, null, null, false, null, null, null, null);
        var resposta = new AgendaMedicoResponse();
        when(service.cadastrar(request)).thenReturn(resposta);

        var response = controller.cadastrar(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar204AoRemover() {
        UUID id = UUID.randomUUID();

        var response = controller.remover(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).remover(id);
    }
}
