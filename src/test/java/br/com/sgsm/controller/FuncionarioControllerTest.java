package br.com.sgsm.controller;

import br.com.sgsm.dto.AtualizarFuncionarioRequest;
import br.com.sgsm.dto.CadastrarFuncionarioRequest;
import br.com.sgsm.dto.FuncionarioResponse;
import br.com.sgsm.service.FuncionarioService;
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
class FuncionarioControllerTest {

    @Mock
    private FuncionarioService service;

    private FuncionarioController controller;

    @BeforeEach
    void setUp() {
        controller = new FuncionarioController(service);
    }

    @Test
    void deveRetornar201AoCadastrar() {
        UUID estabelecimentoId = UUID.randomUUID();
        var request = new CadastrarFuncionarioRequest("Maria Silva", "123.456.789-00", "maria@email.com", null, "Recepcionista", estabelecimentoId);
        var resposta = new FuncionarioResponse();
        when(service.cadastrar(request)).thenReturn(resposta);

        var response = controller.cadastrar(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200AoConsultar() {
        UUID id = UUID.randomUUID();
        var resposta = new FuncionarioResponse();
        when(service.consultar(id)).thenReturn(resposta);

        var response = controller.consultar(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200AoAtualizar() {
        UUID id = UUID.randomUUID();
        var request = new AtualizarFuncionarioRequest("Maria Souza", null, null, null);
        var resposta = new FuncionarioResponse();
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
        var resposta = new FuncionarioResponse();
        when(service.reativar(id)).thenReturn(resposta);

        var response = controller.reativar(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200ComListaAoListar() {
        UUID estabelecimentoId = UUID.randomUUID();
        var resposta = new FuncionarioResponse();
        when(service.listar(estabelecimentoId, true)).thenReturn(List.of(resposta));

        var response = controller.listar(estabelecimentoId, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }

    @Test
    void deveRetornar200ComListaSemFiltrosAoListar() {
        var resposta = new FuncionarioResponse();
        when(service.listar(null, null)).thenReturn(List.of(resposta));

        var response = controller.listar(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }
}
