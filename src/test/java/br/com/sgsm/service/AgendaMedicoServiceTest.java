package br.com.sgsm.service;

import br.com.sgsm.domain.AgendaMedico;
import br.com.sgsm.domain.DiaSemana;
import br.com.sgsm.domain.Estabelecimento;
import br.com.sgsm.domain.Medico;
import br.com.sgsm.dto.CadastrarAgendaMedicoRequest;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.AgendaMedicoRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.MedicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgendaMedicoServiceTest {

    @Mock
    private AgendaMedicoRepository agendaMedicoRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private EstabelecimentoRepository estabelecimentoRepository;

    private AgendaMedicoService service;

    @BeforeEach
    void setUp() {
        service = new AgendaMedicoService(agendaMedicoRepository, medicoRepository, estabelecimentoRepository);
    }

    private CadastrarAgendaMedicoRequest requestPresencial(UUID medicoId, UUID estabelecimentoId) {
        return new CadastrarAgendaMedicoRequest(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, "08:00", "12:00",
                30, LocalDate.of(2026, 1, 1), null, false, null, null, null, null);
    }

    @Test
    void deveListarAgendasOrdenadasDoMedico() {
        UUID medicoId = UUID.randomUUID();
        var agenda = new AgendaMedico();
        agenda.setMedicoId(medicoId);
        agenda.setDiaSemana(DiaSemana.SEGUNDA);
        agenda.setHoraInicio(java.time.LocalTime.of(8, 0));
        agenda.setHoraFim(java.time.LocalTime.of(12, 0));
        when(agendaMedicoRepository.findByMedicoIdOrderByDiaSemanaAscHoraInicioAsc(medicoId))
                .thenReturn(List.of(agenda));

        var resultado = service.listar(medicoId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getMedicoId()).isEqualTo(medicoId);
    }

    @Test
    void deveIncluirNomeDoEstabelecimentoAoListarQuandoPresencial() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        var agenda = new AgendaMedico();
        agenda.setMedicoId(medicoId);
        agenda.setEstabelecimentoId(estabelecimentoId);
        agenda.setDiaSemana(DiaSemana.SEGUNDA);
        agenda.setHoraInicio(java.time.LocalTime.of(8, 0));
        agenda.setHoraFim(java.time.LocalTime.of(12, 0));
        when(agendaMedicoRepository.findByMedicoIdOrderByDiaSemanaAscHoraInicioAsc(medicoId))
                .thenReturn(List.of(agenda));
        var estabelecimento = new Estabelecimento();
        estabelecimento.setNome("Clínica Central");
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(estabelecimento));

        var resultado = service.listar(medicoId);

        assertThat(resultado.get(0).getEstabelecimentoNome()).isEqualTo("Clínica Central");
    }

    @Test
    void deveCadastrarAgendaPresencialQuandoDadosValidos() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(new Estabelecimento()));
        when(agendaMedicoRepository.save(any(AgendaMedico.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cadastrar(requestPresencial(medicoId, estabelecimentoId));

        assertThat(response.getMedicoId()).isEqualTo(medicoId);
        assertThat(response.getEstabelecimentoId()).isEqualTo(estabelecimentoId);
        assertThat(response.getDomiciliar()).isFalse();
    }

    @Test
    void deveCadastrarAgendaDomiciliarSemEstabelecimento() {
        UUID medicoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        when(agendaMedicoRepository.save(any(AgendaMedico.class))).thenAnswer(inv -> inv.getArgument(0));
        var request = new CadastrarAgendaMedicoRequest(
                medicoId, null, DiaSemana.SEGUNDA, "08:00", "12:00",
                30, LocalDate.of(2026, 1, 1), null, true, 10, null, "São Paulo", "SP");

        var response = service.cadastrar(request);

        assertThat(response.getDomiciliar()).isTrue();
        assertThat(response.getEstabelecimentoId()).isNull();
        verifyNoInteractions(estabelecimentoRepository);
    }

    @Test
    void deveLancarExcecaoAoCadastrarComMedicoInexistente() {
        UUID medicoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cadastrar(requestPresencial(medicoId, UUID.randomUUID())))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoQuandoAgendaPresencialSemEstabelecimento() {
        UUID medicoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        var request = new CadastrarAgendaMedicoRequest(
                medicoId, null, DiaSemana.SEGUNDA, "08:00", "12:00",
                30, LocalDate.of(2026, 1, 1), null, false, null, null, null, null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estabelecimento é obrigatório");
    }

    @Test
    void deveLancarExcecaoQuandoEstabelecimentoInexistente() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cadastrar(requestPresencial(medicoId, estabelecimentoId)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoQuandoHoraFimNaoPosteriorAHoraInicio() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(new Estabelecimento()));
        var request = new CadastrarAgendaMedicoRequest(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, "12:00", "08:00",
                30, LocalDate.of(2026, 1, 1), null, false, null, null, null, null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hora fim deve ser posterior");
    }

    @Test
    void deveLancarExcecaoQuandoDuracaoSlotMenorQueMinimo() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(new Estabelecimento()));
        var request = new CadastrarAgendaMedicoRequest(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, "08:00", "12:00",
                3, LocalDate.of(2026, 1, 1), null, false, null, null, null, null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duração mínima");
    }

    @Test
    void deveLancarExcecaoQuandoDuracaoSlotNula() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(new Estabelecimento()));
        var request = new CadastrarAgendaMedicoRequest(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, "08:00", "12:00",
                null, LocalDate.of(2026, 1, 1), null, false, null, null, null, null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveInativarAgendaAoRemover() {
        UUID id = UUID.randomUUID();
        var agenda = new AgendaMedico();
        when(agendaMedicoRepository.findById(id)).thenReturn(Optional.of(agenda));

        service.remover(id);

        assertThat(agenda.getAtivo()).isFalse();
        verify(agendaMedicoRepository).save(agenda);
    }

    @Test
    void deveLancarExcecaoAoRemoverAgendaInexistente() {
        UUID id = UUID.randomUUID();
        when(agendaMedicoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
