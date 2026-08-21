package br.com.sgsm.controller;

import br.com.sgsm.dto.AtualizarPacienteRequest;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.dto.PacienteResponse;
import br.com.sgsm.service.PacienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteControllerTest {

    @Mock
    private PacienteService service;

    private PacienteController controller;

    @BeforeEach
    void setUp() {
        controller = new PacienteController(service);
    }

    @Test
    void deveRetornar201AoCadastrar() {
        var request = new CadastrarPacienteRequest("João Silva", "12345678900",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null);
        var resposta = new PacienteResponse();
        when(service.cadastrar(request)).thenReturn(resposta);

        var response = controller.cadastrar(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200AoConsultar() {
        UUID id = UUID.randomUUID();
        var resposta = new PacienteResponse();
        when(service.consultar(id)).thenReturn(resposta);

        var response = controller.consultar(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200AoAtualizar() {
        UUID id = UUID.randomUUID();
        var request = new AtualizarPacienteRequest("João S. Silva", null, null, null, null, null, null, null, null, null, null);
        var resposta = new PacienteResponse();
        when(service.atualizar(id, request)).thenReturn(resposta);

        var response = controller.atualizar(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar204AoRemover() {
        UUID id = UUID.randomUUID();

        var response = controller.remover(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).remover(id);
    }

    @Test
    void deveRetornar200AoReativar() {
        UUID id = UUID.randomUUID();
        var resposta = new PacienteResponse();
        when(service.reativar(id)).thenReturn(resposta);

        var response = controller.reativar(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200ComListaAoListar() {
        var resposta = new PacienteResponse();
        when(service.listar(true)).thenReturn(List.of(resposta));

        var response = controller.listar(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }
}
