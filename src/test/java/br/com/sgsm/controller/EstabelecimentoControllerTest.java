package br.com.sgsm.controller;

import br.com.sgsm.dto.*;
import br.com.sgsm.service.EstabelecimentoService;
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

@ExtendWith(MockitoExtension.class)
class EstabelecimentoControllerTest {

    @Mock
    private EstabelecimentoService service;

    private EstabelecimentoController controller;

    @BeforeEach
    void setUp() {
        controller = new EstabelecimentoController(service);
    }

    @Test
    void deveRetornar201AoCadastrar() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica Central", "12345678000199", null, null, "Rua A", "100", null, "Centro", "São Paulo", "SP", "01000-000");
        var resposta = new EstabelecimentoResponse();
        when(service.cadastrar(request)).thenReturn(resposta);

        var response = controller.cadastrar(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200AoConsultar() {
        UUID id = UUID.randomUUID();
        var resposta = new EstabelecimentoResponse();
        when(service.consultar(id)).thenReturn(resposta);

        var response = controller.consultar(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200AoAtualizar() {
        UUID id = UUID.randomUUID();
        var request = new AtualizarEstabelecimentoRequest(
                "Clínica Central Ltda", null, null, null, null, null, null, null, null, null);
        var resposta = new EstabelecimentoResponse();
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
        var resposta = new EstabelecimentoResponse();
        when(service.reativar(id)).thenReturn(resposta);

        var response = controller.reativar(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200ComListaAoListar() {
        var resposta = new EstabelecimentoResponse();
        when(service.listar(true, "SP", "São Paulo", null)).thenReturn(List.of(resposta));

        var response = controller.listar(true, "SP", "São Paulo", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }

    @Test
    void deveRetornar200ComListaFiltradaPorMedicoId() {
        UUID medicoId = UUID.randomUUID();
        var resposta = new EstabelecimentoResponse();
        when(service.listar(true, null, null, medicoId)).thenReturn(List.of(resposta));

        var response = controller.listar(true, null, null, medicoId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }

    @Test
    void deveRetornar200ComListaDeMedicosVinculados() {
        UUID id = UUID.randomUUID();
        var medico = new MedicoResponse();
        when(service.listarMedicos(id)).thenReturn(List.of(medico));

        var response = controller.listarMedicos(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(medico);
    }

    @Test
    void deveRetornar204AoAssociarMedicos() {
        UUID id = UUID.randomUUID();
        var request = new AssociarMedicosRequest(List.of(UUID.randomUUID()));

        var response = controller.associarMedicos(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).associarMedicos(id, request);
    }
}
