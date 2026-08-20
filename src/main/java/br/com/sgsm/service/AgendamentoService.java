package br.com.sgsm.service;

import br.com.sgsm.domain.*;
import br.com.sgsm.dto.*;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.*;
import br.com.sgsm.security.ContextoSeguranca;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class AgendamentoService {

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
            VetorizacaoPublisher vetorizacaoPublisher) {
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

    @Transactional(readOnly = true)
    public List<SlotDisponivelResponse> listarSlotsDisponiveis(
            UUID medicoId, UUID estabelecimentoId, LocalDate data) {

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
                    slots.add(new SlotDisponivelResponse(slotInicio, slotFim, duracao));
                }

                cursor = cursor.plusMinutes(passo);
            }
        }

        return slots;
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

        synchronized (locksPorMedico.computeIfAbsent(medicoId, id -> new Object())) {
            OffsetDateTime inicioDia = dataHoraInicio.atZoneSameInstant(ZONA).toLocalDate()
                    .atStartOfDay(ZONA).toOffsetDateTime();
            OffsetDateTime fimDia = inicioDia.plusDays(1);
            var ocupados = agendamentoRepository.findOcupadosByMedicoAndData(
                    medicoId, inicioDia, fimDia, STATUS_IGNORADOS);
            if (estaOcupado(dataHoraInicio, dataHoraFim, ocupados)) {
                throw new IllegalArgumentException(
                        "Horário indisponível: já existe agendamento para este médico neste período.");
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

            var salvo = agendamentoRepository.saveAndFlush(agendamento);
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
    public List<AgendamentoResponse> listar(UUID pacienteId, StatusAgendamento status, UUID medicoId) {
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
