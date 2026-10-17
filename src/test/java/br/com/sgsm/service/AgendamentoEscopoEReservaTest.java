package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.*;
import br.com.sgsm.dto.CadastrarAgendamentoRequest;
import br.com.sgsm.dto.CriarReservaRequest;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.HorarioIndisponivelException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.*;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import br.com.sgsm.service.ReservaHorarioService.Reserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Plano_Desenvolvimento_Conflito_Agendamento_SGSM.docx:
 *  - Fase 1: escopo por papel (secretária = FUNCIONARIO, só a própria clínica)
 *  - Fase 2: violação da constraint excl_sem_conflito_medico vira horário indisponível
 *  - Fase 3: pré-reserva (horários de outros escondidos, reserva própria consumida, alternativas)
 */
@ExtendWith(MockitoExtension.class)
class AgendamentoEscopoEReservaTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final UUID MEDICO = UUID.randomUUID();
    private static final UUID OUTRO_MEDICO = UUID.randomUUID();
    private static final UUID CLINICA_A = UUID.randomUUID();
    private static final UUID CLINICA_B = UUID.randomUUID();
    private static final UUID SERVICO = UUID.randomUUID();
    private static final UUID PACIENTE = UUID.randomUUID();
    // Próxima segunda-feira às 10h: sempre no futuro e num dia com agenda
    private static final LocalDate SEGUNDA = LocalDate.now(ZONA).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final OffsetDateTime DEZ_HORAS = SEGUNDA.atTime(10, 0).atZone(ZONA).toOffsetDateTime();

    @Mock private AgendamentoRepository agendamentoRepository;
    @Mock private AgendaMedicoRepository agendaMedicoRepository;
    @Mock private BloqueioAgendaRepository bloqueioAgendaRepository;
    @Mock private MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    @Mock private ServicoMedicoRepository servicoMedicoRepository;
    @Mock private MedicoRepository medicoRepository;
    @Mock private EstabelecimentoRepository estabelecimentoRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private ContextoSeguranca contexto;
    @Mock private VetorizacaoPublisher vetorizacaoPublisher;
    @Mock private EstabelecimentoAcessoService acesso;
    @Mock private ReservaHorarioService reservas;

    private AgendamentoService service;

    @BeforeEach
    void setUp() {
        service = new AgendamentoService(agendamentoRepository, agendaMedicoRepository, bloqueioAgendaRepository,
                medicoEstabelecimentoRepository, servicoMedicoRepository, medicoRepository, estabelecimentoRepository,
                pacienteRepository, new ModelMapperConfig().modelMapper(), contexto, vetorizacaoPublisher, acesso,
                reservas);
        lenient().when(contexto.getUsuarioId()).thenReturn("secretaria-a");
        lenient().when(acesso.estabelecimentosPermitidos()).thenReturn(null);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private void comoSecretariaDaClinicaA() {
        lenient().when(contexto.isFuncionario()).thenReturn(true);
        lenient().when(acesso.estabelecimentosPermitidos()).thenReturn(List.of(CLINICA_A));
    }

    private void servicoAtivo() {
        var s = new ServicoMedico();
        s.setMedicoId(MEDICO);
        s.setNome("Consulta");
        s.setDuracaoMinutos(30);
        s.setPreco(BigDecimal.TEN);
        s.setAtivo(true);
        when(servicoMedicoRepository.findById(SERVICO)).thenReturn(Optional.of(s));
    }

    private void pacienteEClinicaExistem() {
        lenient().when(pacienteRepository.findById(PACIENTE)).thenReturn(Optional.of(new Paciente()));
        lenient().when(estabelecimentoRepository.findById(any())).thenReturn(Optional.of(new Estabelecimento()));
    }

    private static Agendamento agendamento(UUID medico, UUID clinica, TipoAgendamento tipo) {
        var a = new Agendamento();
        a.setMedicoId(medico);
        a.setPacienteId(PACIENTE);
        a.setServicoMedicoId(SERVICO);
        a.setEstabelecimentoId(clinica);
        a.setTipo(tipo);
        a.setStatus(StatusAgendamento.PENDENTE);
        a.setDataHoraInicio(DEZ_HORAS);
        a.setDataHoraFim(DEZ_HORAS.plusMinutes(30));
        return a;
    }

    // Simula o id gerado pelo banco ao gravar
    private static Agendamento comId(Agendamento a) {
        org.springframework.test.util.ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        return a;
    }

    private static Reserva reserva(String dono, OffsetDateTime inicio) {
        return new Reserva("tok-" + dono, MEDICO, SERVICO, CLINICA_A, "PRESENCIAL", inicio, inicio.plusMinutes(30),
                dono, Instant.now(), Instant.now().plusSeconds(600));
    }

    // Agenda das 9h às 12h, slots de 30 min, na clínica A
    private void agendaDaClinicaA() {
        var agenda = new AgendaMedico();
        agenda.setMedicoId(MEDICO);
        agenda.setEstabelecimentoId(CLINICA_A);
        agenda.setDiaSemana(DiaSemana.SEGUNDA);
        agenda.setHoraInicio(LocalTime.of(9, 0));
        agenda.setHoraFim(LocalTime.of(12, 0));
        agenda.setDuracaoSlotMinutos(30);
        agenda.setDataVigenciaInicio(LocalDate.of(2020, 1, 1));
        agenda.setDomiciliar(false);
        agenda.setAtivo(true);
        lenient().when(agendaMedicoRepository.findByMedicoIdAndEstabelecimentoIdAndDiaSemanaAndAtivo(
                eq(MEDICO), eq(CLINICA_A), any(), eq(true))).thenAnswer(inv ->
                inv.getArgument(2) == DiaSemana.SEGUNDA ? List.of(agenda) : List.of());
    }

    private CadastrarAgendamentoRequest pedido(UUID clinica, TipoAgendamento tipo, String token) {
        return new CadastrarAgendamentoRequest(PACIENTE, SERVICO, clinica, tipo, DEZ_HORAS, null, token);
    }

    // ── Fase 1: escopo por papel ──────────────────────────────────────────────

    @Test
    void secretariaNaoVeGradeNemDiasDeOutraClinica() {
        comoSecretariaDaClinicaA();
        agendaDaClinicaA();

        assertThatThrownBy(() -> service.listarSlotsDisponiveis(MEDICO, CLINICA_B, SEGUNDA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.listarDiasComDisponibilidade(MEDICO, CLINICA_B, YearMonth.from(SEGUNDA)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        // grade domiciliar: vale o vínculo do médico com a clínica da secretária
        when(acesso.medicoPermitido(OUTRO_MEDICO)).thenReturn(false);
        assertThatThrownBy(() -> service.listarSlotsDisponiveis(OUTRO_MEDICO, null, SEGUNDA))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        assertThat(service.listarSlotsDisponiveis(MEDICO, CLINICA_A, SEGUNDA)).isNotEmpty();
        assertThat(service.listarDiasComDisponibilidade(MEDICO, CLINICA_A, YearMonth.from(SEGUNDA))).contains(SEGUNDA);
    }

    @Test
    void medicoSoVeAPropriaGrade() {
        when(contexto.isMedico()).thenReturn(true);
        when(contexto.getReferenciaId()).thenReturn(MEDICO);
        agendaDaClinicaA();

        assertThatThrownBy(() -> service.listarSlotsDisponiveis(OUTRO_MEDICO, CLINICA_A, SEGUNDA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(service.listarSlotsDisponiveis(MEDICO, CLINICA_A, SEGUNDA)).isNotEmpty();
    }

    @Test
    void secretariaNaoAgendaEmOutraClinica() {
        comoSecretariaDaClinicaA();
        servicoAtivo();
        pacienteEClinicaExistem();

        assertThatThrownBy(() -> service.cadastrar(pedido(CLINICA_B, TipoAgendamento.PRESENCIAL, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(agendamentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void secretariaAgendaNaPropriaClinica() {
        comoSecretariaDaClinicaA();
        servicoAtivo();
        pacienteEClinicaExistem();
        when(agendamentoRepository.saveAndFlush(any())).thenAnswer(inv -> comId(inv.getArgument(0)));

        var resposta = service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, null));

        assertThat(resposta.getEstabelecimentoId()).isEqualTo(CLINICA_A);
    }

    @Test
    void secretariaSoAgendaDomiciliarDeMedicoDaClinica() {
        comoSecretariaDaClinicaA();
        servicoAtivo();
        pacienteEClinicaExistem();
        when(acesso.medicoPermitido(MEDICO)).thenReturn(false);

        assertThatThrownBy(() -> service.cadastrar(pedido(null, TipoAgendamento.DOMICILIAR, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void medicoSoAgendaNaPropriaAgenda() {
        when(contexto.isMedico()).thenReturn(true);
        when(contexto.getReferenciaId()).thenReturn(OUTRO_MEDICO);
        servicoAtivo();
        pacienteEClinicaExistem();

        assertThatThrownBy(() -> service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void secretariaNaoConsultaNemCancelaAgendamentoDeOutraClinica() {
        comoSecretariaDaClinicaA();
        UUID id = UUID.randomUUID();
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento(MEDICO, CLINICA_B, TipoAgendamento.PRESENCIAL)));

        assertThatThrownBy(() -> service.consultar(id)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.cancelar(id, new br.com.sgsm.dto.CancelarAgendamentoRequest(
                OrigemCancelamento.ESTABELECIMENTO, "x"))).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarStatus(id, StatusAgendamento.CONFIRMADO, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void adminConsultaDomiciliarDeMedicoVinculadoAsSuasClinicas() {
        when(contexto.isAdminEstabelecimento()).thenReturn(true);
        when(acesso.medicoPermitido(MEDICO)).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(agendamentoRepository.findById(id)).thenReturn(Optional.of(agendamento(MEDICO, null, TipoAgendamento.DOMICILIAR)));

        assertThat(service.consultar(id).getMedicoId()).isEqualTo(MEDICO);
    }

    // ── Fase 2/3: conflito na gravação ────────────────────────────────────────

    @Test
    void horarioOcupadoDeveTrazerAlternativasMaisProximas() {
        servicoAtivo();
        pacienteEClinicaExistem();
        agendaDaClinicaA();
        var ocupado = agendamento(MEDICO, CLINICA_A, TipoAgendamento.PRESENCIAL);
        when(agendamentoRepository.findOcupadosByMedicoAndData(eq(MEDICO), any(), any(), any())).thenReturn(List.of(ocupado));

        assertThatThrownBy(() -> service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, null)))
                .isInstanceOf(HorarioIndisponivelException.class)
                .satisfies(e -> assertThat(((HorarioIndisponivelException) e).getAlternativas())
                        .extracting(s -> s.dataHoraInicio().toLocalTime())
                        // 10h ocupada: as mais próximas são 9h30 e 10h30, depois 9h ou 11h
                        .hasSize(3).contains(LocalTime.of(9, 30), LocalTime.of(10, 30)));
    }

    @Test
    void horarioPreReservadoPorOutroNaoPodeSerGravado() {
        servicoAtivo();
        pacienteEClinicaExistem();
        when(reservas.ativasDoMedico(eq(MEDICO), any(), any())).thenReturn(List.of(reserva("bot-whatsapp", DEZ_HORAS)));

        assertThatThrownBy(() -> service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, null)))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("pré-reserva por outro usuário");
        verify(agendamentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void reservaPropriaEhConsumidaAoGravar() {
        servicoAtivo();
        pacienteEClinicaExistem();
        var minha = reserva("secretaria-a", DEZ_HORAS);
        when(reservas.validarParaConsumo("tok-secretaria-a", "secretaria-a", MEDICO, DEZ_HORAS)).thenReturn(minha);
        when(reservas.ativasDoMedico(eq(MEDICO), any(), any())).thenReturn(List.of(minha));
        when(agendamentoRepository.saveAndFlush(any())).thenAnswer(inv -> comId(inv.getArgument(0)));

        service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, "tok-secretaria-a"));

        // sem transação ativa no teste, a remoção é imediata (com transação, só após o commit)
        verify(reservas).remover(minha);
    }

    @Test
    void falhaAoRemoverReservaConsumidaNaoDesfazOAgendamento() {
        servicoAtivo();
        pacienteEClinicaExistem();
        var minha = reserva("secretaria-a", DEZ_HORAS);
        when(reservas.validarParaConsumo(any(), any(), any(), any())).thenReturn(minha);
        when(agendamentoRepository.saveAndFlush(any())).thenAnswer(inv -> comId(inv.getArgument(0)));
        doThrow(new RedisConnectionFailureException("redis fora")).when(reservas).remover(minha);

        assertThat(service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, "tok"))).isNotNull();
    }

    @Test
    void violacaoDaConstraintDeExclusaoViraHorarioIndisponivel() {
        servicoAtivo();
        pacienteEClinicaExistem();
        when(agendamentoRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("excl",
                new SQLException("conflicting key value violates exclusion constraint", "23P01")));

        assertThatThrownBy(() -> service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, null)))
                .isInstanceOf(HorarioIndisponivelException.class);
    }

    @Test
    void outraViolacaoDeIntegridadeContinuaComoEra() {
        servicoAtivo();
        pacienteEClinicaExistem();
        when(agendamentoRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fk",
                new SQLException("fk", "23503")));

        assertThatThrownBy(() -> service.cadastrar(pedido(CLINICA_A, TipoAgendamento.PRESENCIAL, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Fase 3: horários livres e pré-reserva ─────────────────────────────────

    @Test
    void slotsEscondemReservasDeOutrosEMarcamAsProprias() {
        agendaDaClinicaA();
        when(reservas.ativasDoMedico(eq(MEDICO), any(), any())).thenReturn(List.of(
                reserva("bot-whatsapp", DEZ_HORAS),
                reserva("secretaria-a", DEZ_HORAS.plusMinutes(30))));

        var slots = service.listarSlotsDisponiveis(MEDICO, CLINICA_A, SEGUNDA);

        assertThat(slots).extracting(s -> s.dataHoraInicio().toLocalTime()).doesNotContain(LocalTime.of(10, 0));
        assertThat(slots).filteredOn(s -> s.dataHoraInicio().toLocalTime().equals(LocalTime.of(10, 30)))
                .singleElement().satisfies(s -> assertThat(s.reservadoPorVoce()).isTrue());
    }

    @Test
    void redisForaDoArNaoDerrubaAListaDeHorarios() {
        agendaDaClinicaA();
        when(reservas.ativasDoMedico(any(), any(), any())).thenThrow(new RedisConnectionFailureException("fora"));

        assertThat(service.listarSlotsDisponiveis(MEDICO, CLINICA_A, SEGUNDA)).hasSize(6);
    }

    @Test
    void reservarHorarioLivreDevolveTokenEValidade() {
        servicoAtivo();
        var criada = reserva("secretaria-a", DEZ_HORAS);
        when(reservas.criar(MEDICO, SERVICO, CLINICA_A, "PRESENCIAL", DEZ_HORAS, DEZ_HORAS.plusMinutes(30), "secretaria-a"))
                .thenReturn(criada);
        when(reservas.noFuso(any())).thenAnswer(inv -> ((Instant) inv.getArgument(0)).atZone(ZONA).toOffsetDateTime());

        var resposta = service.reservar(new CriarReservaRequest(SERVICO, CLINICA_A, null, DEZ_HORAS));

        assertThat(resposta.reservado()).isTrue();
        assertThat(resposta.token()).isEqualTo("tok-secretaria-a");
        assertThat(resposta.expiraEm()).isNotNull();
    }

    @Test
    void secretariaNaoReservaAgendaDeOutraClinica() {
        comoSecretariaDaClinicaA();
        servicoAtivo();

        assertThatThrownBy(() -> service.reservar(new CriarReservaRequest(SERVICO, CLINICA_B, null, DEZ_HORAS)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(reservas);
    }

    @Test
    void reservarHorarioOcupadoDevolve409ComAlternativas() {
        servicoAtivo();
        agendaDaClinicaA();
        when(agendamentoRepository.findOcupadosByMedicoAndData(eq(MEDICO), any(), any(), any()))
                .thenReturn(List.of(agendamento(MEDICO, CLINICA_A, TipoAgendamento.PRESENCIAL)));

        assertThatThrownBy(() -> service.reservar(new CriarReservaRequest(SERVICO, CLINICA_A, null, DEZ_HORAS)))
                .isInstanceOf(HorarioIndisponivelException.class)
                .satisfies(e -> assertThat(((HorarioIndisponivelException) e).getAlternativas()).isNotEmpty());
    }

    @Test
    void reservaJaFeitaPorOutroDevolve409ComAlternativas() {
        servicoAtivo();
        agendaDaClinicaA();
        when(reservas.criar(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new HorarioIndisponivelException("Horário em pré-reserva por outro usuário.", List.of()));

        assertThatThrownBy(() -> service.reservar(new CriarReservaRequest(SERVICO, CLINICA_A, null, DEZ_HORAS)))
                .isInstanceOf(HorarioIndisponivelException.class)
                .satisfies(e -> assertThat(((HorarioIndisponivelException) e).getAlternativas()).isNotEmpty());
    }

    @Test
    void redisForaDoArNaReservaFalhaAberto() {
        servicoAtivo();
        when(reservas.criar(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("fora"));

        var resposta = service.reservar(new CriarReservaRequest(SERVICO, CLINICA_A, null, DEZ_HORAS));

        assertThat(resposta.reservado()).isFalse();
        assertThat(resposta.token()).isNull();
    }

    @Test
    void reservaInvalidaEhRecusada() {
        assertThatThrownBy(() -> service.reservar(new CriarReservaRequest(SERVICO, CLINICA_A, null, DEZ_HORAS)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        var inativo = new ServicoMedico();
        inativo.setMedicoId(MEDICO);
        inativo.setAtivo(false);
        when(servicoMedicoRepository.findById(SERVICO)).thenReturn(Optional.of(inativo));
        assertThatThrownBy(() -> service.reservar(new CriarReservaRequest(SERVICO, CLINICA_A, null, DEZ_HORAS)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoReservaHorarioNoPassado() {
        servicoAtivo();

        assertThatThrownBy(() -> service.reservar(new CriarReservaRequest(SERVICO, CLINICA_A, null,
                OffsetDateTime.now(ZONA).minusHours(1)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renovarELiberarSaoDoProprioUsuario() {
        when(reservas.renovar("tok", "secretaria-a")).thenReturn(reserva("secretaria-a", DEZ_HORAS));
        when(reservas.noFuso(any())).thenAnswer(inv -> ((Instant) inv.getArgument(0)).atZone(ZONA).toOffsetDateTime());

        assertThat(service.renovarReserva("tok").reservado()).isTrue();
        service.liberarReserva("tok");

        verify(reservas).liberar("tok", "secretaria-a");
    }

    @Test
    void alternativasDoBotSemClinicaUsamTodasAsClinicasDoMedico() {
        agendaDaClinicaA();
        when(medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(MEDICO, true))
                .thenReturn(List.of(new MedicoEstabelecimento(MEDICO, CLINICA_A)));

        var alternativas = service.alternativas(MEDICO, null, TipoAgendamento.PRESENCIAL, DEZ_HORAS);

        assertThat(alternativas).hasSize(3).noneMatch(s -> s.dataHoraInicio().isEqual(DEZ_HORAS));
    }

    @Test
    void alternativasDomiciliarUsamAAgendaDomiciliar() {
        when(agendaMedicoRepository.findByMedicoIdAndDomiciliarTrueAndDiaSemanaAndAtivo(eq(MEDICO), any(), eq(true)))
                .thenReturn(List.of());

        assertThat(service.alternativas(MEDICO, CLINICA_A, TipoAgendamento.DOMICILIAR, DEZ_HORAS)).isEmpty();
        verify(agendaMedicoRepository, atLeastOnce()).findByMedicoIdAndDomiciliarTrueAndDiaSemanaAndAtivo(eq(MEDICO), any(), eq(true));
    }
}
