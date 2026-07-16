package br.com.sgsm.controller;

import br.com.sgsm.domain.OrigemCancelamento;
import br.com.sgsm.domain.StatusAgendamento;
import br.com.sgsm.domain.TipoAgendamento;
import br.com.sgsm.dto.*;
import br.com.sgsm.service.AgendamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendamentoControllerTest {

    @Mock
    private AgendamentoService service;

    private AgendamentoController controller;

    @BeforeEach
    void setUp() {
        controller = new AgendamentoController(service);
    }

    @Test
    void deveRetornar200ComListaDeEstabelecimentosPorMedico() {
        UUID medicoId = UUID.randomUUID();
        var resposta = new EstabelecimentoResponse();
        when(service.listarEstabelecimentosPorMedico(medicoId)).thenReturn(List.of(resposta));

        var response = controller.listarEstabelecimentosPorMedico(medicoId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }

    @Test
    void deveRetornar200ComListaDeSlots() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 7, 20);
        var slot = new SlotDisponivelResponse(OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30), 30);
        when(service.listarSlotsDisponiveis(medicoId, estabelecimentoId, data)).thenReturn(List.of(slot));

        var response = controller.listarSlots(medicoId, estabelecimentoId, data);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(slot);
    }

    @Test
    void deveRetornar201AoCadastrar() {
        var request = new CadastrarAgendamentoRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, TipoAgendamento.PRESENCIAL, OffsetDateTime.now(), null);
        var resposta = new AgendamentoResponse();
        when(service.cadastrar(request)).thenReturn(resposta);

        var response = controller.cadastrar(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200AoConsultar() {
        UUID id = UUID.randomUUID();
        var resposta = new AgendamentoResponse();
        when(service.consultar(id)).thenReturn(resposta);

        var response = controller.consultar(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar200ComListaAoListar() {
        UUID pacienteId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        var resposta = new AgendamentoResponse();
        when(service.listar(pacienteId, StatusAgendamento.PENDENTE, medicoId)).thenReturn(List.of(resposta));

        var response = controller.listar(pacienteId, StatusAgendamento.PENDENTE, medicoId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resposta);
    }

    @Test
    void deveRetornar200AoAtualizarStatus() {
        UUID id = UUID.randomUUID();
        var request = new AtualizarStatusAgendamentoRequest(StatusAgendamento.CONFIRMADO, null);
        var resposta = new AgendamentoResponse();
        when(service.atualizarStatus(id, StatusAgendamento.CONFIRMADO, null)).thenReturn(resposta);

        var response = controller.atualizarStatus(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resposta);
    }

    @Test
    void deveRetornar204AoCancelar() {
        UUID id = UUID.randomUUID();
        var request = new CancelarAgendamentoRequest(OrigemCancelamento.PACIENTE, "Imprevisto");

        var response = controller.cancelar(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).cancelar(id, request);
    }
}
