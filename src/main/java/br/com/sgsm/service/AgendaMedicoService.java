package br.com.sgsm.service;

import br.com.sgsm.domain.AgendaMedico;
import br.com.sgsm.dto.AgendaMedicoResponse;
import br.com.sgsm.dto.CadastrarAgendaMedicoRequest;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.AgendaMedicoRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.MedicoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AgendaMedicoService {

    private final AgendaMedicoRepository agendaMedicoRepository;
    private final MedicoRepository medicoRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final ContextoSeguranca contextoSeguranca;

    public AgendaMedicoService(
            AgendaMedicoRepository agendaMedicoRepository,
            MedicoRepository medicoRepository,
            EstabelecimentoRepository estabelecimentoRepository,
            ContextoSeguranca contextoSeguranca) {
        this.agendaMedicoRepository = agendaMedicoRepository;
        this.medicoRepository = medicoRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.contextoSeguranca = contextoSeguranca;
    }

    @Transactional(readOnly = true)
    public List<AgendaMedicoResponse> listar(UUID medicoId) {
        verificarPosse(medicoId);
        return agendaMedicoRepository
                .findByMedicoIdOrderByDiaSemanaAscHoraInicioAsc(medicoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AgendaMedicoResponse cadastrar(CadastrarAgendaMedicoRequest request) {
        verificarPosse(request.medicoId());
        medicoRepository.findById(request.medicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Médico não encontrado: " + request.medicoId()));

        boolean domiciliar = Boolean.TRUE.equals(request.domiciliar());

        if (!domiciliar && request.estabelecimentoId() != null) {
            estabelecimentoRepository.findById(request.estabelecimentoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + request.estabelecimentoId()));
        } else if (!domiciliar) {
            throw new IllegalArgumentException("Estabelecimento é obrigatório para agenda presencial");
        }

        LocalTime inicio = LocalTime.parse(request.horaInicio());
        LocalTime fim = LocalTime.parse(request.horaFim());

        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("Hora fim deve ser posterior à hora início");
        }
        if (request.duracaoSlotMinutos() == null || request.duracaoSlotMinutos() < 5) {
            throw new IllegalArgumentException("Duração mínima do slot é 5 minutos");
        }

        var agenda = new AgendaMedico();
        agenda.setMedicoId(request.medicoId());
        agenda.setEstabelecimentoId(domiciliar ? null : request.estabelecimentoId());
        agenda.setDiaSemana(request.diaSemana());
        agenda.setHoraInicio(inicio);
        agenda.setHoraFim(fim);
        agenda.setDuracaoSlotMinutos(request.duracaoSlotMinutos());
        agenda.setDataVigenciaInicio(request.dataVigenciaInicio());
        agenda.setDataVigenciaFim(request.dataVigenciaFim());
        agenda.setDomiciliar(domiciliar);
        agenda.setIntervaloDeslocamentoMinutos(request.intervaloDeslocamentoMinutos());
        agenda.setRaioKm(request.raioKm());
        agenda.setCidadeAtendimento(request.cidadeAtendimento());
        agenda.setUfAtendimento(request.ufAtendimento());

        return toResponse(agendaMedicoRepository.save(agenda));
    }

    public void remover(UUID id) {
        var agenda = agendaMedicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agenda não encontrada: " + id));
        verificarPosse(agenda.getMedicoId());
        agenda.setAtivo(false);
        agendaMedicoRepository.save(agenda);
    }

    private void verificarPosse(UUID medicoId) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isMedico() && !medicoId.equals(ref)) {
            throw new AcessoNegadoException("Médico não pode gerenciar agenda de outro médico: " + medicoId);
        }
    }

    private AgendaMedicoResponse toResponse(AgendaMedico a) {
        var r = new AgendaMedicoResponse();
        r.setId(a.getId());
        r.setMedicoId(a.getMedicoId());
        r.setEstabelecimentoId(a.getEstabelecimentoId());
        r.setDiaSemana(a.getDiaSemana());
        r.setHoraInicio(a.getHoraInicio());
        r.setHoraFim(a.getHoraFim());
        r.setDuracaoSlotMinutos(a.getDuracaoSlotMinutos());
        r.setDataVigenciaInicio(a.getDataVigenciaInicio());
        r.setDataVigenciaFim(a.getDataVigenciaFim());
        r.setDomiciliar(a.getDomiciliar());
        r.setIntervaloDeslocamentoMinutos(a.getIntervaloDeslocamentoMinutos());
        r.setRaioKm(a.getRaioKm());
        r.setCidadeAtendimento(a.getCidadeAtendimento());
        r.setUfAtendimento(a.getUfAtendimento());
        r.setAtivo(a.getAtivo());
        r.setCriadoEm(a.getCriadoEm());

        if (a.getEstabelecimentoId() != null) {
            estabelecimentoRepository.findById(a.getEstabelecimentoId())
                    .ifPresent(e -> r.setEstabelecimentoNome(e.getNome()));
        }

        return r;
    }
}
