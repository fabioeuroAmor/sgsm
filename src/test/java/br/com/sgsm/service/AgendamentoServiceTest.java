package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.*;
import br.com.sgsm.dto.*;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.events.NotificacaoPublisher;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.repository.*;
import br.com.sgsm.security.ContextoSeguranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private AgendaMedicoRepository agendaMedicoRepository;
    @Mock
    private BloqueioAgendaRepository bloqueioAgendaRepository;
    @Mock
    private MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    @Mock
    private ServicoMedicoRepository servicoMedicoRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private EstabelecimentoRepository estabelecimentoRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ContextoSeguranca contextoSeguranca;
    @Mock
    private VetorizacaoPublisher vetorizacaoPublisher;
    @Mock
    private NotificacaoPublisher notificacaoPublisher;

    private AgendamentoService service;

    @BeforeEach
    void setUp() {
        service = new AgendamentoService(
                agendamentoRepository, agendaMedicoRepository, bloqueioAgendaRepository,
                medicoEstabelecimentoRepository, servicoMedicoRepository, medicoRepository,
                estabelecimentoRepository, pacienteRepository,
                new ModelMapperConfig().modelMapper(), contextoSeguranca, vetorizacaoPublisher,
                notificacaoPublisher);
    }

    // ---------- Fixtures ----------

    private Estabelecimento novoEstabelecimento(boolean ativo) {
        var e = new Estabelecimento();
        e.setNome("Clínica Central");
        e.setAtivo(ativo);
        e.setLogradouro("Rua A");
        e.setNumero("100");
        e.setBairro("Centro");
        e.setCidade("São Paulo");
        e.setUf("SP");
        e.setCep("01000-000");
        return e;
    }

    private ServicoMedico novoServico(UUID medicoId, boolean ativo) {
        var s = new ServicoMedico();
        s.setMedicoId(medicoId);
        s.setNome("Consulta");
        s.setDuracaoMinutos(30);
        s.setPreco(BigDecimal.TEN);
        s.setAtivo(ativo);
        return s;
    }

    private Paciente novoPaciente() {
        var p = new Paciente();
        p.setNome("João Silva");
        p.setLogradouro("Rua B");
        p.setNumero("200");
        p.setBairro("Jardim");
        p.setCidade("São Paulo");
        p.setUf("SP");
        p.setCep("02000-000");
        return p;
    }

    private Agendamento novoAgendamento(UUID medicoId, UUID pacienteId, StatusAgendamento status, TipoAgendamento tipo) {
        var a = new Agendamento();
        a.setMedicoId(medicoId);
        a.setPacienteId(pacienteId);
        a.setServicoMedicoId(UUID.randomUUID());
        a.setStatus(status);
        a.setTipo(tipo);
        a.setDataHoraInicio(OffsetDateTime.now());
        a.setDataHoraFim(OffsetDateTime.now().plusMinutes(30));
        return a;
    }

    // ---------- listarEstabelecimentosPorMedico ----------

    @Test
    void deveListarTodosEstabelecimentosAtivosQuandoMedicoSemVinculo() {
        UUID medicoId = UUID.randomUUID();
        when(medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(medicoId, true)).thenReturn(List.of());
        when(estabelecimentoRepository.findAllByAtivo(true)).thenReturn(List.of(novoEstabelecimento(true)));

        var resultado = service.listarEstabelecimentosPorMedico(medicoId);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarApenasEstabelecimentosVinculadosEAtivos() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        UUID estabelecimentoInativoId = UUID.randomUUID();
        var vinculo = new MedicoEstabelecimento(medicoId, estabelecimentoId);
        var vinculoInativo = new MedicoEstabelecimento(medicoId, estabelecimentoInativoId);
        when(medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(medicoId, true))
                .thenReturn(List.of(vinculo, vinculoInativo));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(novoEstabelecimento(true)));
        when(estabelecimentoRepository.findById(estabelecimentoInativoId)).thenReturn(Optional.of(novoEstabelecimento(false)));

        var resultado = service.listarEstabelecimentosPorMedico(medicoId);

        assertThat(resultado).hasSize(1);
    }

    // ---------- listarSlotsDisponiveis ----------

    @Test
    void deveRetornarListaVaziaQuandoNaoHaAgendaParaODia() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 7, 20); // segunda-feira
        when(agendaMedicoRepository.findByMedicoIdAndEstabelecimentoIdAndDiaSemanaAndAtivo(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, true)).thenReturn(List.of());

        var resultado = service.listarSlotsDisponiveis(medicoId, estabelecimentoId, data);

        assertThat(resultado).isEmpty();
        verifyNoInteractions(bloqueioAgendaRepository, agendamentoRepository);
    }

    @Test
    void deveGerarSlotsPresenciaisRespeitandoBloqueiosEOcupados() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 7, 20); // segunda-feira

        var agenda = new AgendaMedico();
        agenda.setHoraInicio(java.time.LocalTime.of(8, 0));
        agenda.setHoraFim(java.time.LocalTime.of(9, 0));
        agenda.setDuracaoSlotMinutos(30);
        agenda.setDataVigenciaInicio(LocalDate.of(2026, 1, 1));
        agenda.setDomiciliar(false);

        when(agendaMedicoRepository.findByMedicoIdAndEstabelecimentoIdAndDiaSemanaAndAtivo(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, true)).thenReturn(List.of(agenda));
        when(bloqueioAgendaRepository.findByMedicoAndPeriodo(eq(medicoId), eq(estabelecimentoId), any(), any()))
                .thenReturn(List.of());
        when(agendamentoRepository.findOcupadosByMedicoAndData(eq(medicoId), any(), any(), anyList()))
                .thenReturn(List.of());

        var resultado = service.listarSlotsDisponiveis(medicoId, estabelecimentoId, data);

        assertThat(resultado).hasSize(2); // 08:00-08:30 e 08:30-09:00
    }

    @Test
    void deveExcluirSlotsComBloqueioOuAgendamentoConflitante() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 7, 20); // segunda-feira

        var agenda = new AgendaMedico();
        agenda.setHoraInicio(java.time.LocalTime.of(8, 0));
        agenda.setHoraFim(java.time.LocalTime.of(9, 0));
        agenda.setDuracaoSlotMinutos(30);
        agenda.setDataVigenciaInicio(LocalDate.of(2026, 1, 1));
        agenda.setDomiciliar(false);

        OffsetDateTime inicioDia = data.atStartOfDay(ZONA).toOffsetDateTime();
        var bloqueio = new BloqueioAgenda();
        setPrivateField(bloqueio, "dataHoraInicio", inicioDia.withHour(8));
        setPrivateField(bloqueio, "dataHoraFim", inicioDia.withHour(8).plusMinutes(30));

        var ocupado = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CONFIRMADO, TipoAgendamento.PRESENCIAL);
        ocupado.setDataHoraInicio(inicioDia.withHour(8).plusMinutes(30));
        ocupado.setDataHoraFim(inicioDia.withHour(9));

        when(agendaMedicoRepository.findByMedicoIdAndEstabelecimentoIdAndDiaSemanaAndAtivo(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, true)).thenReturn(List.of(agenda));
        when(bloqueioAgendaRepository.findByMedicoAndPeriodo(eq(medicoId), eq(estabelecimentoId), any(), any()))
                .thenReturn(List.of(bloqueio));
        when(agendamentoRepository.findOcupadosByMedicoAndData(eq(medicoId), any(), any(), anyList()))
                .thenReturn(List.of(ocupado));

        var resultado = service.listarSlotsDisponiveis(medicoId, estabelecimentoId, data);

        assertThat(resultado).isEmpty(); // 08:00-08:30 bloqueado, 08:30-09:00 ocupado
    }

    private void setPrivateField(Object target, String nome, Object valor) {
        try {
            var field = target.getClass().getDeclaredField(nome);
            field.setAccessible(true);
            field.set(target, valor);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void deveGerarSlotsDomiciliaresComIntervaloDeDeslocamento() {
        UUID medicoId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 7, 20); // segunda-feira

        var agenda = new AgendaMedico();
        agenda.setHoraInicio(java.time.LocalTime.of(8, 0));
        agenda.setHoraFim(java.time.LocalTime.of(9, 0));
        agenda.setDuracaoSlotMinutos(20);
        agenda.setDataVigenciaInicio(LocalDate.of(2026, 1, 1));
        agenda.setDomiciliar(true);
        agenda.setIntervaloDeslocamentoMinutos(10);

        when(agendaMedicoRepository.findByMedicoIdAndDomiciliarTrueAndDiaSemanaAndAtivo(
                medicoId, DiaSemana.SEGUNDA, true)).thenReturn(List.of(agenda));
        when(bloqueioAgendaRepository.findByMedicoAndPeriodo(eq(medicoId), eq(null), any(), any()))
                .thenReturn(List.of());
        when(agendamentoRepository.findOcupadosByMedicoAndData(eq(medicoId), any(), any(), anyList()))
                .thenReturn(List.of());

        var resultado = service.listarSlotsDisponiveis(medicoId, null, data);

        // passo = 20+10=30min; 08:00, 08:30 cabem (09:00 exato não permite +20min ultrapassar)
        assertThat(resultado).hasSize(2);
    }

    @Test
    void deveExcluirAgendaForaDaVigencia() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 7, 20);

        var agenda = new AgendaMedico();
        agenda.setHoraInicio(java.time.LocalTime.of(8, 0));
        agenda.setHoraFim(java.time.LocalTime.of(9, 0));
        agenda.setDuracaoSlotMinutos(30);
        agenda.setDataVigenciaInicio(LocalDate.of(2026, 8, 1)); // início futuro: fora de vigência

        when(agendaMedicoRepository.findByMedicoIdAndEstabelecimentoIdAndDiaSemanaAndAtivo(
                medicoId, estabelecimentoId, DiaSemana.SEGUNDA, true)).thenReturn(List.of(agenda));

        var resultado = service.listarSlotsDisponiveis(medicoId, estabelecimentoId, data);

        assertThat(resultado).isEmpty();
    }

    // ---------- cadastrar ----------

    @Test
    void deveCadastrarAgendamentoPresencialQuandoDadosValidos() {
        UUID pacienteId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var servico = novoServico(medicoId, true);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(novoPaciente()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(novoEstabelecimento(true)));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CadastrarAgendamentoRequest(pacienteId, servicoId, estabelecimentoId,
                TipoAgendamento.PRESENCIAL, OffsetDateTime.now(), "obs");

        var response = service.cadastrar(request);

        assertThat(response.getMedicoId()).isEqualTo(medicoId);
        assertThat(response.getEstabelecimentoId()).isEqualTo(estabelecimentoId);
    }

    @Test
    void deveCadastrarAgendamentoComTipoPadraoPresencialQuandoNaoInformado() {
        UUID pacienteId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(novoServico(medicoId, true)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(novoPaciente()));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CadastrarAgendamentoRequest(pacienteId, servicoId, null, null, OffsetDateTime.now(), null);

        var response = service.cadastrar(request);

        assertThat(response.getTipo()).isEqualTo(TipoAgendamento.PRESENCIAL);
    }

    @Test
    void deveCadastrarAgendamentoDomiciliarComEnderecoDoPaciente() {
        UUID pacienteId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(novoServico(medicoId, true)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(novoPaciente()));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CadastrarAgendamentoRequest(pacienteId, servicoId, null,
                TipoAgendamento.DOMICILIAR, OffsetDateTime.now(), null);

        var response = service.cadastrar(request);

        assertThat(response.getTipo()).isEqualTo(TipoAgendamento.DOMICILIAR);
        assertThat(response.getEstabelecimentoId()).isNull();
        assertThat(response.getPacienteEndereco()).contains("Rua B", "200", "Jardim");
    }

    @Test
    void deveUsarDuracaoPadraoDe30MinutosQuandoServicoSemDuracao() {
        UUID pacienteId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var servico = novoServico(medicoId, true);
        servico.setDuracaoMinutos(null);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(novoPaciente()));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));
        OffsetDateTime inicio = OffsetDateTime.now();

        var request = new CadastrarAgendamentoRequest(pacienteId, servicoId, null,
                TipoAgendamento.DOMICILIAR, inicio, null);

        var response = service.cadastrar(request);

        assertThat(response.getDataHoraFim()).isEqualTo(inicio.plusMinutes(30));
    }

    @Test
    void deveNegarCadastroQuandoPacienteAgendaParaOutroPaciente() {
        UUID pacienteAutenticado = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(pacienteAutenticado);

        var request = new CadastrarAgendamentoRequest(UUID.randomUUID(), UUID.randomUUID(), null,
                TipoAgendamento.PRESENCIAL, OffsetDateTime.now(), null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(servicoMedicoRepository);
    }

    @Test
    void devePermitirCadastroQuandoPacienteAgendaParaSiMesmo() {
        UUID pacienteId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(pacienteId);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(novoServico(medicoId, true)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(novoPaciente()));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CadastrarAgendamentoRequest(pacienteId, servicoId, null,
                TipoAgendamento.DOMICILIAR, OffsetDateTime.now(), null);

        var response = service.cadastrar(request);

        assertThat(response).isNotNull();
    }

    @Test
    void deveLancarExcecaoQuandoServicoMedicoInexistente() {
        UUID servicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.empty());

        var request = new CadastrarAgendamentoRequest(UUID.randomUUID(), servicoId, null,
                TipoAgendamento.PRESENCIAL, OffsetDateTime.now(), null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoQuandoServicoMedicoInativo() {
        UUID servicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(novoServico(UUID.randomUUID(), false)));

        var request = new CadastrarAgendamentoRequest(UUID.randomUUID(), servicoId, null,
                TipoAgendamento.PRESENCIAL, OffsetDateTime.now(), null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void deveLancarExcecaoQuandoPacienteInexistente() {
        UUID pacienteId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(novoServico(UUID.randomUUID(), true)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.empty());

        var request = new CadastrarAgendamentoRequest(pacienteId, servicoId, null,
                TipoAgendamento.PRESENCIAL, OffsetDateTime.now(), null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoQuandoEstabelecimentoInexistenteNoCadastro() {
        UUID pacienteId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(novoServico(UUID.randomUUID(), true)));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(novoPaciente()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.empty());

        var request = new CadastrarAgendamentoRequest(pacienteId, servicoId, estabelecimentoId,
                TipoAgendamento.PRESENCIAL, OffsetDateTime.now(), null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---------- consultar ----------

    @Test
    void deveConsultarAgendamentoQuandoAcessoPermitido() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        UUID pacienteId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, pacienteId, StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));

        var response = service.consultar(id);

        assertThat(response.getMedicoId()).isEqualTo(medicoId);
    }

    @Test
    void deveNegarConsultaQuandoMedicoNaoEDonoDoAgendamento() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void deveNegarConsultaQuandoPacienteNaoEDonoDoAgendamento() {
        UUID id = UUID.randomUUID();
        UUID pacienteId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());
        var agendamento = novoAgendamento(UUID.randomUUID(), pacienteId, StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void deveLancarExcecaoAoConsultarAgendamentoInexistente() {
        UUID id = UUID.randomUUID();
        when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---------- listar ----------

    @Test
    void deveListarPorMedicoESatusQuandoAutenticadoComoMedico() {
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(medicoId);
        when(agendamentoRepository.findAllByMedicoIdAndStatus(medicoId, StatusAgendamento.PENDENTE))
                .thenReturn(List.of(novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL)));

        var resultado = service.listar(null, StatusAgendamento.PENDENTE, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorPacienteEStatusQuandoAutenticadoComoPaciente() {
        UUID pacienteId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(pacienteId);
        when(agendamentoRepository.findAllByPacienteIdAndStatus(pacienteId, StatusAgendamento.CONFIRMADO))
                .thenReturn(List.of(novoAgendamento(UUID.randomUUID(), pacienteId, StatusAgendamento.CONFIRMADO, TipoAgendamento.PRESENCIAL)));

        var resultado = service.listar(null, StatusAgendamento.CONFIRMADO, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorMedicoQuandoApenasMedicoIdInformado() {
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(agendamentoRepository.findAllByMedicoId(medicoId))
                .thenReturn(List.of(novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL)));

        var resultado = service.listar(null, null, medicoId);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorPacienteQuandoApenasPacienteIdInformado() {
        UUID pacienteId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(agendamentoRepository.findAllByPacienteId(pacienteId))
                .thenReturn(List.of(novoAgendamento(UUID.randomUUID(), pacienteId, StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL)));

        var resultado = service.listar(pacienteId, null, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorStatusQuandoApenasStatusInformado() {
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(agendamentoRepository.findAllByStatus(StatusAgendamento.CANCELADO))
                .thenReturn(List.of());

        var resultado = service.listar(null, StatusAgendamento.CANCELADO, null);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveListarTodosQuandoNenhumFiltroInformadoENaoAutenticadoComoMedicoOuPaciente() {
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(agendamentoRepository.findAll()).thenReturn(List.of());

        var resultado = service.listar(null, null, null);

        assertThat(resultado).isEmpty();
    }

    // ---------- atualizarStatus ----------

    @Test
    void deveConfirmarAgendamentoPendente() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.CONFIRMADO, null);

        assertThat(response.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        verify(notificacaoPublisher).publicar(eq("CONFIRMACAO_AGENDAMENTO"), anyString(), eq(agendamento.getPacienteId().toString()));
    }

    @Test
    void deveConfirmarAgendamentoAguardandoPagamento() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.AGUARDANDO_PAGAMENTO, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.CONFIRMADO, null);

        assertThat(response.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
    }

    @Test
    void naoDevePublicarNotificacaoAoMarcarComoEmAndamento() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CONFIRMADO, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });

        service.atualizarStatus(id, StatusAgendamento.EM_ANDAMENTO, null);

        verifyNoInteractions(notificacaoPublisher);
    }

    @Test
    void deveMarcarComoACaminhoAtualizandoLocalizacaoQuandoDomiciliar() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CONFIRMADO, TipoAgendamento.DOMICILIAR);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.A_CAMINHO, "-23.55,-46.63");

        assertThat(response.getStatus()).isEqualTo(StatusAgendamento.A_CAMINHO);
        assertThat(response.getLocalizacaoMedico()).isEqualTo("-23.55,-46.63");
    }

    @Test
    void naoDeveAtualizarLocalizacaoQuandoBrancaMesmoParaACaminho() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CONFIRMADO, TipoAgendamento.DOMICILIAR);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.A_CAMINHO, "   ");

        assertThat(response.getLocalizacaoMedico()).isNull();
    }

    @Test
    void deveLancarExcecaoAoTentarACaminhoQuandoNaoDomiciliar() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CONFIRMADO, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));

        assertThatThrownBy(() -> service.atualizarStatus(id, StatusAgendamento.A_CAMINHO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transição inválida");
    }

    @Test
    void deveMarcarComoChegouQuandoEstavaACaminho() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.A_CAMINHO, TipoAgendamento.DOMICILIAR);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.CHEGOU, null);

        assertThat(response.getStatus()).isEqualTo(StatusAgendamento.CHEGOU);
    }

    @Test
    void deveIniciarAtendimentoQuandoConfirmado() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CONFIRMADO, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.EM_ANDAMENTO, null);

        assertThat(response.getStatus()).isEqualTo(StatusAgendamento.EM_ANDAMENTO);
    }

    @Test
    void deveIniciarAtendimentoQuandoChegou() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CHEGOU, TipoAgendamento.DOMICILIAR);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.EM_ANDAMENTO, null);

        assertThat(response.getStatus()).isEqualTo(StatusAgendamento.EM_ANDAMENTO);
    }

    @Test
    void deveConcluirAgendamentoEmAndamento() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.EM_ANDAMENTO, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.atualizarStatus(id, StatusAgendamento.CONCLUIDO, null);

        assertThat(response.getStatus()).isEqualTo(StatusAgendamento.CONCLUIDO);
    }

    @Test
    void deveLancarExcecaoParaTransicaoInvalidaDeStatus() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));

        assertThatThrownBy(() -> service.atualizarStatus(id, StatusAgendamento.CONCLUIDO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transição inválida");
    }

    @Test
    void deveLancarExcecaoParaTransicaoDefaultNaoMapeada() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));

        assertThatThrownBy(() -> service.atualizarStatus(id, StatusAgendamento.CANCELADO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- cancelar ----------

    @Test
    void deveCancelarAgendamentoPendente() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        var request = new CancelarAgendamentoRequest(OrigemCancelamento.PACIENTE, "Imprevisto");

        service.cancelar(id, request);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getOrigemCancelamento()).isEqualTo(OrigemCancelamento.PACIENTE);
        assertThat(agendamento.getMotivoCancelamento()).isEqualTo("Imprevisto");
        verify(agendamentoRepository).save(agendamento);
    }

    @Test
    void deveLancarExcecaoAoCancelarAgendamentoJaCancelado() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CANCELADO, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        var request = new CancelarAgendamentoRequest(OrigemCancelamento.PACIENTE, "x");

        assertThatThrownBy(() -> service.cancelar(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já está cancelado");
    }

    @Test
    void deveLancarExcecaoAoCancelarAgendamentoConcluido() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.CONCLUIDO, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        var request = new CancelarAgendamentoRequest(OrigemCancelamento.MEDICO, "x");

        assertThatThrownBy(() -> service.cancelar(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já concluído");
    }

    @Test
    void deveNegarCancelamentoQuandoMedicoNaoEDonoDoAgendamento() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());
        var agendamento = novoAgendamento(medicoId, UUID.randomUUID(), StatusAgendamento.PENDENTE, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento));
        var request = new CancelarAgendamentoRequest(OrigemCancelamento.MEDICO, "x");

        assertThatThrownBy(() -> service.cancelar(id, request))
                .isInstanceOf(AcessoNegadoException.class);
    }
}
