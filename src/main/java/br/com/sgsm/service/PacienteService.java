package br.com.sgsm.service;

import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.Paciente;
import br.com.sgsm.dto.AtualizarPacienteRequest;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.dto.PacienteResponse;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.AgendamentoRepository;
import br.com.sgsm.repository.PacienteRepository;
import br.com.sgsm.security.ContextoSeguranca;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PacienteService {

    private final PacienteRepository repository;
    private final AgendamentoRepository agendamentoRepository;
    private final ModelMapper modelMapper;
    private final ContextoSeguranca contextoSeguranca;
    private final VetorizacaoPublisher vetorizacaoPublisher;

    public PacienteService(PacienteRepository repository,
                           AgendamentoRepository agendamentoRepository,
                           ModelMapper modelMapper,
                           ContextoSeguranca contextoSeguranca,
                           VetorizacaoPublisher vetorizacaoPublisher) {
        this.repository = repository;
        this.agendamentoRepository = agendamentoRepository;
        this.modelMapper = modelMapper;
        this.contextoSeguranca = contextoSeguranca;
        this.vetorizacaoPublisher = vetorizacaoPublisher;
    }

    // UC - Cadastrar paciente
    public PacienteResponse cadastrar(CadastrarPacienteRequest request) {
        if (!cpfValido(request.cpf())) {
            throw new IllegalArgumentException("CPF inválido: " + request.cpf());
        }
        if (repository.existsByCpf(request.cpf())) {
            throw new IllegalArgumentException("CPF já cadastrado: " + request.cpf());
        }
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }

        var paciente = modelMapper.map(request, Paciente.class);
        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "CREATE");
        return modelMapper.map(salvo, PacienteResponse.class);
    }

    // UC - Consultar paciente
    @Transactional(readOnly = true)
    public PacienteResponse consultar(UUID id) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isPaciente() && !id.equals(ref)) {
            throw new AcessoNegadoException("Acesso negado ao paciente: " + id);
        }
        if (contextoSeguranca.isMedico()) {
            boolean temAgendamento = agendamentoRepository.findAllByMedicoId(ref)
                    .stream().anyMatch(a -> a.getPacienteId().equals(id));
            if (!temAgendamento) {
                throw new AcessoNegadoException("Acesso negado ao paciente: " + id);
            }
        }
        return repository.findById(id)
                .map(p -> modelMapper.map(p, PacienteResponse.class))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: " + id));
    }

    // UC - Atualizar paciente
    public PacienteResponse atualizar(UUID id, AtualizarPacienteRequest request) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isPaciente() && !id.equals(ref)) {
            throw new AcessoNegadoException("Paciente não pode editar dados de outro paciente: " + id);
        }
        var paciente = buscarOuLancarErro(id);

        if (request.email() != null && repository.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }

        modelMapper.map(request, paciente);
        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "UPDATE");
        return modelMapper.map(salvo, PacienteResponse.class);
    }

    // UC - Remover (inativar) paciente
    public void remover(UUID id) {
        var paciente = buscarOuLancarErro(id);
        paciente.setAtivo(false);
        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "UPDATE");
    }

    // UC - Reativar paciente
    public PacienteResponse reativar(UUID id) {
        var paciente = buscarOuLancarErro(id);
        paciente.setAtivo(true);
        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "UPDATE");
        return modelMapper.map(salvo, PacienteResponse.class);
    }

    // UC - Listar pacientes
    @Transactional(readOnly = true)
    public List<PacienteResponse> listar(Boolean ativo) {
        if (contextoSeguranca.isPaciente()) {
            UUID id = contextoSeguranca.getReferenciaId();
            return repository.findById(id)
                    .map(p -> List.of(modelMapper.map(p, PacienteResponse.class)))
                    .orElse(List.of());
        }
        List<Paciente> resultado = (ativo != null)
                ? repository.findAllByAtivo(ativo)
                : repository.findAll();
        return resultado.stream()
                .map(p -> modelMapper.map(p, PacienteResponse.class))
                .toList();
    }

    private Paciente buscarOuLancarErro(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: " + id));
    }

    private boolean cpfValido(String cpf) {
        String d = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = calcularDigitoVerificadorCpf(d, 9);
        int dv2 = calcularDigitoVerificadorCpf(d, 10);
        return dv1 == Character.getNumericValue(d.charAt(9))
                && dv2 == Character.getNumericValue(d.charAt(10));
    }

    private int calcularDigitoVerificadorCpf(String d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += Character.getNumericValue(d.charAt(i)) * (tamanho + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return (resto == 10 || resto == 11) ? 0 : resto;
    }
}
