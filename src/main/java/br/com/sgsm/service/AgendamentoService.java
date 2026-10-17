package br.com.sgsm.service;

import br.com.sgsm.domain.*;
import br.com.sgsm.dto.*;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.GlobalExceptionHandler;
import br.com.sgsm.exception.HorarioIndisponivelException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.*;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class AgendamentoService {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoService.class);
    private static final int MAX_ALTERNATIVAS = 3;
    private static final int DIAS_BUSCA_ALTERNATIVAS = 7;
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final List<StatusAgendamento> STATUS_IGNORADOS =
            List.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);
    private final ConcurrentHashMap<UUID, Object> locksPorMedico = new ConcurrentHashMap<>();

    private final AgendamentoRepository agendamentoRepository;
    private final AgendaMedicoRepository agendaMedicoRepository;
    private final BloqueioAgendaRepository bloqueioAgendaRepository;
    private final MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    private final ServicoMedicoRepository servicoMedicoRepository;
    private final MedicoRepository medicoRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final PacienteRepository pacienteRepository;
    private final ModelMapper modelMapper;
    private final ContextoSeguranca contextoSeguranca;
    private final VetorizacaoPublisher vetorizacaoPublisher;
    private final EstabelecimentoAcessoService estabelecimentoAcessoService;
    private final ReservaHorarioService reservaHorarioService;

    public AgendamentoService(
            AgendamentoRepository agendamentoRepository,
            AgendaMedicoRepository agendaMedicoRepository,
            BloqueioAgendaRepository bloqueioAgendaRepository,
            MedicoEstabelecimentoRepository medicoEstabelecimentoRepository,
            ServicoMedicoRepository servicoMedicoRepository,
            MedicoRepository medicoRepository,
            EstabelecimentoRepository estabelecimentoRepository,
            PacienteRepository pacienteRepository,
            ModelMapper modelMapper,
            ContextoSeguranca contextoSeguranca,
            VetorizacaoPublisher vetorizacaoPublisher,
            EstabelecimentoAcessoService estabelecimentoAcessoService,
            ReservaHorarioService reservaHorarioService) {
        this.agendamentoRepository = agendamentoRepository;
        this.agendaMedicoRepository = agendaMedicoRepository;
        this.bloqueioAgendaRepository = bloqueioAgendaRepository;
        this.medicoEstabelecimentoRepository = medicoEstabelecimentoRepository;
        this.servicoMedicoRepository = servicoMedicoRepository;
        this.medicoRepository = medicoRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.modelMapper = modelMapper;
        this.contextoSeguranca = contextoSeguranca;
        this.vetorizacaoPublisher = vetorizacaoPublisher;
        this.estabelecimentoAcessoService = estabelecimentoAcessoService;
        this.reservaHorarioService = reservaHorarioService;
    }

    @Transactional(readOnly = true)
    public List<EstabelecimentoResponse> listarEstabelecimentosPorMedico(UUID medicoId) {
        var vinculos = medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(medicoId, true);

        List<Estabelecimento> lista;
        if (vinculos.isEmpty()) {
            lista = estabelecimentoRepository.findAllByAtivo(true);
        } else {
            lista = vinculos.stream()
                    .map(v -> estabelecimentoRepository.findById(v.getId().getEstabelecimentoId()).orElse(null))
                    .filter(e -> e != null && Boolean.TRUE.equals(e.getAtivo()))
                    .toList();
        }

        return lista.stream()
                .map(e -> modelMapper.map(e, EstabelecimentoResponse.class))
                .toList();
    }

    // Grade de horários livres: FUNCIONARIO/ADMIN_ESTABELECIMENTO só enxergam agendas das suas
    // clínicas e MEDICO só a própria (mesma regra do agendamento — 404 fora do escopo)
    @Transactional(readOnly = true)
    public List<SlotDisponivelResponse> listarSlotsDisponiveis(
            UUID medicoId, UUID estabelecimentoId, LocalDate data) {
        validarEscopoAgenda(medicoId, estabelecimentoId, tipoDaGrade(estabelecimentoId));
        return calcularSlots(medicoId, estabelecimentoId, data);
    }

    private static TipoAgendamento tipoDaGrade(UUID estabelecimentoId) {
        return estabelecimentoId != null ? TipoAgendamento.PRESENCIAL : TipoAgendamento.DOMICILIAR;
    }

    private List<SlotDisponivelResponse> calcularSlots(UUID medicoId, UUID estabelecimentoId, LocalDate data) {
        DiaSemana diaSemana = DiaSemana.from(data.getDayOfWeek());

        var agendas = (estabelecimentoId != null
                ? agendaMedicoRepository.findByMedicoIdAndEstabelecimentoIdAndDiaSemanaAndAtivo(
                        medicoId, estabelecimentoId, diaSemana, true)
                : agendaMedicoRepository.findByMedicoIdAndDomiciliarTrueAndDiaSemanaAndAtivo(
                        medicoId, diaSemana, true))
                .stream()
                .filter(a -> !data.isBefore(a.getDataVigenciaInicio())
                        && (a.getDataVigenciaFim() == null || !data.isAfter(a.getDataVigenciaFim())))
                .toList();

        if (agendas.isEmpty()) {
            return List.of();
        }

        OffsetDateTime inicioDia = data.atStartOfDay(ZONA).toOffsetDateTime();
        OffsetDateTime fimDia = data.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime();

        var bloqueios = bloqueioAgendaRepository.findByMedicoAndPeriodo(
                medicoId, estabelecimentoId, inicioDia, fimDia);

        var ocupados = agendamentoRepository.findOcupadosByMedicoAndData(
                medicoId, inicioDia, fimDia, STATUS_IGNORADOS);
        var reservas = reservasDoMedico(medicoId, inicioDia, fimDia);
        String dono = contextoSeguranca.getUsuarioId();

        List<SlotDisponivelResponse> slots = new ArrayList<>();
        OffsetDateTime agora = OffsetDateTime.now(ZONA);

        for (AgendaMedico agenda : agendas) {
            LocalTime cursor = agenda.getHoraInicio();
            int duracao = agenda.getDuracaoSlotMinutos();
            int intervalo = (Boolean.TRUE.equals(agenda.getDomiciliar())
                    && agenda.getIntervaloDeslocamentoMinutos() != null)
                    ? agenda.getIntervaloDeslocamentoMinutos() : 0;
            int passo = duracao + intervalo;

            while (!cursor.plusMinutes(duracao).isAfter(agenda.getHoraFim())) {
                OffsetDateTime slotInicio = data.atTime(cursor).atZone(ZONA).toOffsetDateTime();
                OffsetDateTime slotFim = slotInicio.plusMinutes(duracao);

                if (!slotInicio.isBefore(agora)
                        && !temBloqueio(slotInicio, slotFim, bloqueios)
                        && !estaOcupado(slotInicio, slotFim, ocupados)) {
                    // Pré-reservado por outro usuário: some da lista; pelo próprio: aparece marcado
                    boolean deOutro = false;
                    boolean meu = false;
                    for (var r : reservas) {
                        if (r.inicio().isBefore(slotFim) && r.fim().isAfter(slotInicio)) {
                            if (r.dono().equals(dono)) {
                                meu = true;
                            } else {
                                deOutro = true;
                            }
                        }
                    }
                    if (!deOutro) {
                        slots.add(new SlotDisponivelResponse(slotInicio, slotFim, duracao, meu));
                    }
                }

                cursor = cursor.plusMinutes(passo);
            }
        }

        return slots;
    }

    @Transactional(readOnly = true)
    public List<LocalDate> listarDiasComDisponibilidade(UUID medicoId, UUID estabelecimentoId, YearMonth mes) {
        validarEscopoAgenda(medicoId, estabelecimentoId, tipoDaGrade(estabelecimentoId));
        List<LocalDate> dias = new ArrayList<>();
        LocalDate fim = mes.atEndOfMonth();
        for (LocalDate data = mes.atDay(1); !data.isAfter(fim); data = data.plusDays(1)) {
            if (!calcularSlots(medicoId, estabelecimentoId, data).isEmpty()) {
                dias.add(data);
            }
        }
        return dias;
    }

    public AgendamentoResponse cadastrar(CadastrarAgendamentoRequest request) {
        if (contextoSeguranca.isPaciente()) {
            UUID ref = contextoSeguranca.getReferenciaId();
            if (!ref.equals(request.pacienteId())) {
                throw new AcessoNegadoException("Paciente não pode agendar para outro paciente.");
            }
        }

        var servico = servicoMedicoRepository.findById(request.servicoMedicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "ServicoMedico não encontrado: " + request.servicoMedicoId()));

        if (!Boolean.TRUE.equals(servico.getAtivo())) {
            throw new IllegalArgumentException("Serviço médico inativo: " + request.servicoMedicoId());
        }

        pacienteRepository.findById(request.pacienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Paciente não encontrado: " + request.pacienteId()));

        TipoAgendamento tipo = request.tipo() != null ? request.tipo() : TipoAgendamento.PRESENCIAL;

        if (tipo != TipoAgendamento.DOMICILIAR && request.estabelecimentoId() != null) {
            estabelecimentoRepository.findById(request.estabelecimentoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Estabelecimento não encontrado: " + request.estabelecimentoId()));
        }

        int duracao = servico.getDuracaoMinutos() != null ? servico.getDuracaoMinutos() : 30;
        OffsetDateTime dataHoraInicio = request.dataHoraInicio();
        OffsetDateTime dataHoraFim = dataHoraInicio.plusMinutes(duracao);
        UUID medicoId = servico.getMedicoId();
        validarEscopoAgenda(medicoId, request.estabelecimentoId(), tipo);
        String dono = contextoSeguranca.getUsuarioId();
        var reservaPropria = request.reservaToken() == null || request.reservaToken().isBlank() ? null
                : reservaHorarioService.validarParaConsumo(request.reservaToken(), dono, medicoId, dataHoraInicio);

        // A trava em memória é só otimização (vale numa instância e é liberada antes do commit);
        // a garantia é a constraint excl_sem_conflito_medico (V013)
        synchronized (locksPorMedico.computeIfAbsent(medicoId, id -> new Object())) {
            OffsetDateTime inicioDia = dataHoraInicio.atZoneSameInstant(ZONA).toLocalDate()
                    .atStartOfDay(ZONA).toOffsetDateTime();
            OffsetDateTime fimDia = inicioDia.plusDays(1);
            var ocupados = agendamentoRepository.findOcupadosByMedicoAndData(
                    medicoId, inicioDia, fimDia, STATUS_IGNORADOS);
            if (estaOcupado(dataHoraInicio, dataHoraFim, ocupados)) {
                throw new HorarioIndisponivelException(
                        "Horário indisponível: já existe agendamento para este médico neste período.",
                        alternativas(medicoId, request.estabelecimentoId(), tipo, dataHoraInicio));
            }
            if (existeReservaDeOutro(medicoId, dataHoraInicio, dataHoraFim, dono)) {
                throw new HorarioIndisponivelException(
                        "Horário em pré-reserva por outro usuário. Escolha outro horário.",
                        alternativas(medicoId, request.estabelecimentoId(), tipo, dataHoraInicio));
            }

            var agendamento = new Agendamento();
            agendamento.setPacienteId(request.pacienteId());
            agendamento.setServicoMedicoId(request.servicoMedicoId());
            agendamento.setMedicoId(medicoId);
            agendamento.setEstabelecimentoId(tipo == TipoAgendamento.DOMICILIAR ? null : request.estabelecimentoId());
            agendamento.setTipo(tipo);
            agendamento.setDataHoraInicio(dataHoraInicio);
            agendamento.setDataHoraFim(dataHoraFim);
            agendamento.setObservacoes(request.observacoes());

            Agendamento salvo;
            try {
                salvo = agendamentoRepository.saveAndFlush(agendamento);
            } catch (DataIntegrityViolationException e) {
                // Corrida entre réplicas/canais: a constraint barrou o segundo. A transação já está
                // perdida, então não dá para consultar alternativas aqui
                if (GlobalExceptionHandler.violacaoDeExclusao(e)) {
                    throw new HorarioIndisponivelException(
                            "Horário indisponível: já existe agendamento para este médico neste período.", List.of());
                }
                throw e;
            }
            if (reservaPropria != null) {
                removerReservaAposCommit(reservaPropria);
            }
            vetorizacaoPublisher.publicar("AGENDAMENTO", salvo.getId().toString(), "CREATE");
            return toResponse(salvo);
        }
    }

    @Transactional(readOnly = true)
    public AgendamentoResponse consultar(UUID id) {
        var agendamento = buscarOuLancarErro(id);
        validarAcesso(agendamento);
        return toResponse(agendamento);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listar(UUID pacienteId, StatusAgendamento status, UUID medicoId, LocalDate data) {
        if (contextoSeguranca.isMedico()) {
            medicoId = contextoSeguranca.getReferenciaId();
        } else if (contextoSeguranca.isPaciente()) {
            pacienteId = contextoSeguranca.getReferenciaId();
        }

        List<Agendamento> resultado;

        if (medicoId != null && pacienteId != null && status != null) {
            resultado = agendamentoRepository.findAllByMedicoIdAndPacienteIdAndStatus(medicoId, pacienteId, status);
        } else if (medicoId != null && pacienteId != null) {
            resultado = agendamentoRepository.findAllByMedicoIdAndPacienteId(medicoId, pacienteId);
        } else if (medicoId != null && status != null) {
            resultado = agendamentoRepository.findAllByMedicoIdAndStatus(medicoId, status);
        } else if (pacienteId != null && status != null) {
            resultado = agendamentoRepository.findAllByPacienteIdAndStatus(pacienteId, status);
        } else if (medicoId != null) {
            resultado = agendamentoRepository.findAllByMedicoId(medicoId);
        } else if (pacienteId != null) {
            resultado = agendamentoRepository.findAllByPacienteId(pacienteId);
        } else if (status != null) {
            resultado = agendamentoRepository.findAllByStatus(status);
        } else {
            resultado = agendamentoRepository.findAll();
        }

        if (data != null) {
            resultado = resultado.stream()
                    .filter(a -> a.getDataHoraInicio().atZoneSameInstant(ZONA).toLocalDate().equals(data))
                    .toList();
        }

        // FUNCIONARIO/ADMIN_ESTABELECIMENTO: restringe ao(s) estabelecimento(s) vinculado(s).
        // Agendamento domiciliar tem estabelecimentoId nulo e fica de fora aqui - staff
        // administrativo enxerga o agendamento presencial da propria clinica, nao a visita
        // em domicilio (esse caso e tratado a parte na visibilidade de paciente, Parte G).
        List<UUID> estabelecimentosPermitidos = estabelecimentoAcessoService.estabelecimentosPermitidos();
        if (estabelecimentosPermitidos != null) {
            resultado = resultado.stream()
                    .filter(a -> a.getEstabelecimentoId() != null
                            && estabelecimentosPermitidos.contains(a.getEstabelecimentoId()))
                    .toList();
        }

        return resultado.stream().map(this::toResponse).toList();
    }

    public AgendamentoResponse atualizarStatus(UUID id, StatusAgendamento novoStatus, String localizacaoMedico) {
        var agendamento = buscarOuLancarErro(id);
        validarAcesso(agendamento);
        StatusAgendamento atual = agendamento.getStatus();

        boolean valido = switch (novoStatus) {
            case CONFIRMADO -> atual == StatusAgendamento.PENDENTE || atual == StatusAgendamento.AGUARDANDO_PAGAMENTO;
            case A_CAMINHO -> atual == StatusAgendamento.CONFIRMADO && agendamento.getTipo() == TipoAgendamento.DOMICILIAR;
            case CHEGOU -> atual == StatusAgendamento.A_CAMINHO;
            case EM_ANDAMENTO -> atual == StatusAgendamento.CONFIRMADO || atual == StatusAgendamento.CHEGOU;
            case CONCLUIDO -> atual == StatusAgendamento.EM_ANDAMENTO;
            default -> false;
        };

        if (!valido) {
            throw new IllegalArgumentException("Transição inválida: " + atual + " → " + novoStatus);
        }

        agendamento.setStatus(novoStatus);
        if (novoStatus == StatusAgendamento.A_CAMINHO && localizacaoMedico != null && !localizacaoMedico.isBlank()) {
            agendamento.setLocalizacaoMedico(localizacaoMedico);
        }
        var salvo = agendamentoRepository.save(agendamento);
        vetorizacaoPublisher.publicar("AGENDAMENTO", salvo.getId().toString(), "UPDATE");
        return toResponse(salvo);
    }

    public void cancelar(UUID id, CancelarAgendamentoRequest request) {
        var agendamento = buscarOuLancarErro(id);
        validarAcesso(agendamento);

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            throw new IllegalArgumentException("Agendamento já está cancelado: " + id);
        }
        if (agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new IllegalArgumentException("Agendamento já concluído não pode ser cancelado: " + id);
        }

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        agendamento.setOrigemCancelamento(request.origemCancelamento());
        agendamento.setMotivoCancelamento(request.motivoCancelamento());
        agendamentoRepository.save(agendamento);
    }

    private void validarAcesso(Agendamento agendamento) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isMedico() && !agendamento.getMedicoId().equals(ref)) {
            throw new AcessoNegadoException("Acesso negado ao agendamento: " + agendamento.getId());
        }
        if (contextoSeguranca.isPaciente() && !agendamento.getPacienteId().equals(ref)) {
            throw new AcessoNegadoException("Acesso negado ao agendamento: " + agendamento.getId());
        }
        // Secretária (FUNCIONARIO) e ADMIN_ESTABELECIMENTO: só agendamentos das suas clínicas —
        // presencial pelo estabelecimento, domiciliar pelo médico vinculado. Fora disso = 404
        if (contextoSeguranca.isFuncionario() || contextoSeguranca.isAdminEstabelecimento()) {
            if (!agendaNoEscopoDaClinica(agendamento.getMedicoId(), agendamento.getEstabelecimentoId(),
                    agendamento.getTipo())) {
                throw new RecursoNaoEncontradoException("Agendamento não encontrado: " + agendamento.getId());
            }
        }
    }

    // Quem pode agendar/reservar em qual agenda (DA2 do plano de conflito de agendamento):
    // FUNCIONARIO e ADMIN_ESTABELECIMENTO só médicos das suas clínicas; MEDICO só a própria agenda;
    // PACIENTE para si mesmo em qualquer médico ofertado (validado à parte); DESENVOLVEDOR qualquer
    private void validarEscopoAgenda(UUID medicoId, UUID estabelecimentoId, TipoAgendamento tipo) {
        if (contextoSeguranca.isMedico() && !medicoId.equals(contextoSeguranca.getReferenciaId())) {
            throw new RecursoNaoEncontradoException("Agenda não encontrada para o médico: " + medicoId);
        }
        if ((contextoSeguranca.isFuncionario() || contextoSeguranca.isAdminEstabelecimento())
                && !agendaNoEscopoDaClinica(medicoId, estabelecimentoId, tipo)) {
            throw new RecursoNaoEncontradoException("Agenda não encontrada para o médico: " + medicoId);
        }
    }

    private boolean agendaNoEscopoDaClinica(UUID medicoId, UUID estabelecimentoId, TipoAgendamento tipo) {
        if (tipo != TipoAgendamento.DOMICILIAR && estabelecimentoId != null) {
            List<UUID> permitidos = estabelecimentoAcessoService.estabelecimentosPermitidos();
            return permitidos == null || permitidos.contains(estabelecimentoId);
        }
        return estabelecimentoAcessoService.medicoPermitido(medicoId);
    }

    // ── pré-reserva de horário (Fase 3) ───────────────────────────────────────

    public ReservaResponse reservar(CriarReservaRequest request) {
        var servico = servicoMedicoRepository.findById(request.servicoMedicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "ServicoMedico não encontrado: " + request.servicoMedicoId()));
        if (!Boolean.TRUE.equals(servico.getAtivo())) {
            throw new IllegalArgumentException("Serviço médico inativo: " + request.servicoMedicoId());
        }
        if (request.dataHoraInicio() == null || request.dataHoraInicio().isBefore(OffsetDateTime.now(ZONA))) {
            throw new IllegalArgumentException("Horário inválido para pré-reserva.");
        }
        TipoAgendamento tipo = request.tipo() != null ? request.tipo() : TipoAgendamento.PRESENCIAL;
        UUID estabelecimentoId = tipo == TipoAgendamento.DOMICILIAR ? null : request.estabelecimentoId();
        UUID medicoId = servico.getMedicoId();
        validarEscopoAgenda(medicoId, estabelecimentoId, tipo);

        int duracao = servico.getDuracaoMinutos() != null ? servico.getDuracaoMinutos() : 30;
        OffsetDateTime inicio = request.dataHoraInicio();
        OffsetDateTime fim = inicio.plusMinutes(duracao);
        OffsetDateTime inicioDia = inicio.atZoneSameInstant(ZONA).toLocalDate().atStartOfDay(ZONA).toOffsetDateTime();
        var ocupados = agendamentoRepository.findOcupadosByMedicoAndData(
                medicoId, inicioDia, inicioDia.plusDays(1), STATUS_IGNORADOS);
        if (estaOcupado(inicio, fim, ocupados)) {
            throw new HorarioIndisponivelException(
                    "Horário indisponível: já existe agendamento para este médico neste período.",
                    alternativas(medicoId, estabelecimentoId, tipo, inicio));
        }
        try {
            var reserva = reservaHorarioService.criar(medicoId, request.servicoMedicoId(), estabelecimentoId, tipo.name(),
                    inicio, fim, contextoSeguranca.getUsuarioId());
            return new ReservaResponse(reserva.token(), inicio, fim, reservaHorarioService.noFuso(reserva.expiraEm()),
                    reservaHorarioService.noFuso(reserva.renovavelAte()), true);
        } catch (HorarioIndisponivelException e) {
            throw new HorarioIndisponivelException(e.getMessage(), alternativas(medicoId, estabelecimentoId, tipo, inicio));
        } catch (DataAccessException e) {
            // Redis fora do ar: falha aberta — o fluxo segue sem pré-reserva e a constraint do
            // banco continua impedindo dois agendamentos no mesmo horário
            log.warn("Pré-reserva indisponível (Redis): {}", e.getMessage());
            return new ReservaResponse(null, inicio, fim, null, null, false);
        }
    }

    public ReservaResponse renovarReserva(String token) {
        var reserva = reservaHorarioService.renovar(token, contextoSeguranca.getUsuarioId());
        return new ReservaResponse(reserva.token(), reserva.inicio(), reserva.fim(),
                reservaHorarioService.noFuso(reserva.expiraEm()), reservaHorarioService.noFuso(reserva.renovavelAte()), true);
    }

    public void liberarReserva(String token) {
        reservaHorarioService.liberar(token, contextoSeguranca.getUsuarioId());
    }

    private List<ReservaHorarioService.Reserva> reservasDoMedico(UUID medicoId, OffsetDateTime de, OffsetDateTime ate) {
        try {
            return reservaHorarioService.ativasDoMedico(medicoId, de, ate);
        } catch (DataAccessException e) {
            log.warn("Pré-reservas indisponíveis (Redis) — horários listados sem considerá-las: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean existeReservaDeOutro(UUID medicoId, OffsetDateTime inicio, OffsetDateTime fim, String dono) {
        return reservasDoMedico(medicoId, inicio, fim).stream().anyMatch(r -> !r.dono().equals(dono));
    }

    // A reserva só some depois do commit: se a gravação falhar, o horário continua segurado
    private void removerReservaAposCommit(ReservaHorarioService.Reserva reserva) {
        Runnable remover = () -> {
            try {
                reservaHorarioService.remover(reserva);
            } catch (DataAccessException e) {
                log.warn("Não foi possível remover a pré-reserva consumida {}: {}", reserva.token(), e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    remover.run();
                }
            });
        } else {
            remover.run();
        }
    }

    // Horários livres mais próximos do pedido, do mesmo médico, nos próximos dias
    List<SlotDisponivelResponse> alternativas(UUID medicoId, UUID estabelecimentoId, TipoAgendamento tipo,
                                              OffsetDateTime pedido) {
        List<UUID> estabelecimentos = new ArrayList<>();
        if (tipo == TipoAgendamento.DOMICILIAR) {
            estabelecimentos.add(null);
        } else if (estabelecimentoId != null) {
            estabelecimentos.add(estabelecimentoId);
        } else {
            // Presencial sem estabelecimento (ex.: bot de WhatsApp): qualquer clínica do médico
            medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(medicoId, true)
                    .forEach(v -> estabelecimentos.add(v.getId().getEstabelecimentoId()));
        }
        LocalDate dia = pedido.atZoneSameInstant(ZONA).toLocalDate();
        List<SlotDisponivelResponse> encontrados = new ArrayList<>();
        for (int i = 0; i < DIAS_BUSCA_ALTERNATIVAS && encontrados.size() < MAX_ALTERNATIVAS; i++) {
            for (UUID estabelecimento : estabelecimentos) {
                encontrados.addAll(calcularSlots(medicoId, estabelecimento, dia.plusDays(i)));
            }
        }
        return encontrados.stream()
                .filter(s -> !s.dataHoraInicio().isEqual(pedido))
                .sorted(Comparator.comparingLong(s -> Math.abs(Duration.between(pedido, s.dataHoraInicio()).toMinutes())))
                .limit(MAX_ALTERNATIVAS)
                .toList();
    }

    private AgendamentoResponse toResponse(Agendamento a) {
        var response = new AgendamentoResponse();
        response.setId(a.getId());
        response.setPacienteId(a.getPacienteId());
        response.setServicoMedicoId(a.getServicoMedicoId());
        response.setMedicoId(a.getMedicoId());
        response.setEstabelecimentoId(a.getEstabelecimentoId());
        response.setTipo(a.getTipo());
        response.setPagamentoId(a.getPagamentoId());
        response.setDataHoraInicio(a.getDataHoraInicio());
        response.setDataHoraFim(a.getDataHoraFim());
        response.setStatus(a.getStatus());
        response.setObservacoes(a.getObservacoes());
        response.setOrigemCancelamento(a.getOrigemCancelamento());
        response.setMotivoCancelamento(a.getMotivoCancelamento());
        response.setLocalizacaoMedico(a.getLocalizacaoMedico());
        response.setCriadoEm(a.getCriadoEm());
        response.setAtualizadoEm(a.getAtualizadoEm());

        pacienteRepository.findById(a.getPacienteId())
                .ifPresent(p -> {
                    response.setPacienteNome(p.getNome());
                    if (a.getTipo() == TipoAgendamento.DOMICILIAR) {
                        response.setPacienteEndereco(buildEnderecoPaciente(p));
                    }
                });
        servicoMedicoRepository.findById(a.getServicoMedicoId())
                .ifPresent(s -> response.setServicoMedicoNome(s.getNome()));
        medicoRepository.findById(a.getMedicoId())
                .ifPresent(m -> response.setMedicoNome(m.getNome()));
        if (a.getEstabelecimentoId() != null) {
            estabelecimentoRepository.findById(a.getEstabelecimentoId())
                    .ifPresent(e -> {
                        response.setEstabelecimentoNome(e.getNome());
                        response.setEstabelecimentoEndereco(buildEndereco(e));
                    });
        }

        return response;
    }

    private String buildEndereco(br.com.sgsm.domain.Estabelecimento e) {
        return java.util.stream.Stream.of(
                        e.getLogradouro(), e.getNumero(), e.getComplemento(),
                        e.getBairro(), e.getCidade(), e.getUf(), e.getCep(), "Brasil")
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String buildEnderecoPaciente(br.com.sgsm.domain.Paciente p) {
        return java.util.stream.Stream.of(
                        p.getLogradouro(), p.getNumero(), p.getComplemento(),
                        p.getBairro(), p.getCidade(), p.getUf(), p.getCep(), "Brasil")
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private Agendamento buscarOuLancarErro(UUID id) {
        return agendamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado: " + id));
    }

    private boolean temBloqueio(OffsetDateTime inicio, OffsetDateTime fim, List<BloqueioAgenda> bloqueios) {
        return bloqueios.stream().anyMatch(b ->
                inicio.isBefore(b.getDataHoraFim()) && fim.isAfter(b.getDataHoraInicio()));
    }

    private boolean estaOcupado(OffsetDateTime inicio, OffsetDateTime fim, List<Agendamento> ocupados) {
        return ocupados.stream().anyMatch(a ->
                inicio.isBefore(a.getDataHoraFim()) && fim.isAfter(a.getDataHoraInicio()));
    }
}
