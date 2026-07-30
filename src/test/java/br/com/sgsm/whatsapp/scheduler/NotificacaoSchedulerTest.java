package br.com.sgsm.whatsapp.scheduler;

import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.StatusAgendamento;
import br.com.sgsm.events.NotificacaoPublisher;
import br.com.sgsm.repository.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoSchedulerTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private NotificacaoPublisher notificacaoPublisher;
    @Mock
    private JdbcTemplate jdbc;

    private NotificacaoScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificacaoScheduler(agendamentoRepository, notificacaoPublisher, jdbc);
    }

    private Agendamento agendamento(UUID pacienteId) {
        var a = new Agendamento();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(a, "id", id);
        a.setPacienteId(pacienteId);
        return a;
    }

    @Test
    void devePublicarLembretePara24hE2hAntes() {
        UUID paciente24h = UUID.randomUUID();
        UUID paciente2h = UUID.randomUUID();
        var agendamento24h = agendamento(paciente24h);
        var agendamento2h = agendamento(paciente2h);

        when(agendamentoRepository.findAllByStatusAndDataHoraInicioBetween(
                eq(StatusAgendamento.CONFIRMADO), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(agendamento24h))
                .thenReturn(List.of(agendamento2h));

        scheduler.enviarLembretesDeConsulta();

        verify(notificacaoPublisher).publicar("LEMBRETE_CONSULTA", agendamento24h.getId().toString(), paciente24h.toString());
        verify(notificacaoPublisher).publicar("LEMBRETE_CONSULTA", agendamento2h.getId().toString(), paciente2h.toString());
    }

    @Test
    void naoPublicaNadaQuandoNaoHaAgendamentosNaJanela() {
        when(agendamentoRepository.findAllByStatusAndDataHoraInicioBetween(
                any(), any(), any())).thenReturn(List.of());

        scheduler.enviarLembretesDeConsulta();

        verifyNoInteractions(notificacaoPublisher);
    }

    @Test
    void devePublicarRecuperacaoParaCadaPacienteEmChurn() {
        UUID paciente1 = UUID.randomUUID();
        UUID paciente2 = UUID.randomUUID();
        when(jdbc.queryForList("SELECT paciente_id::text FROM crm.v_churn_risco", String.class))
                .thenReturn(List.of(paciente1.toString(), paciente2.toString()));

        scheduler.recuperarPacientesInativos();

        verify(notificacaoPublisher).publicar("RECUPERACAO_INATIVO", null, paciente1.toString());
        verify(notificacaoPublisher).publicar("RECUPERACAO_INATIVO", null, paciente2.toString());
    }
}
