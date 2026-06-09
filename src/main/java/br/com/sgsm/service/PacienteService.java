package br.com.sgsm.service;

import br.com.sgsm.domain.Paciente;
import br.com.sgsm.dto.AtualizarPacienteRequest;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.dto.PacienteResponse;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.PacienteRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PacienteService {

    private final PacienteRepository repository;
    private final ModelMapper modelMapper;

    public PacienteService(PacienteRepository repository, ModelMapper modelMapper) {
        this.repository = repository;
        this.modelMapper = modelMapper;
    }

    // UC - Cadastrar paciente
    public PacienteResponse cadastrar(CadastrarPacienteRequest request) {
        if (repository.existsByCpf(request.cpf())) {
            throw new IllegalArgumentException("CPF já cadastrado: " + request.cpf());
        }
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }

        var paciente = modelMapper.map(request, Paciente.class);

        return modelMapper.map(repository.save(paciente), PacienteResponse.class);
    }

    // UC - Consultar paciente
    @Transactional(readOnly = true)
    public PacienteResponse consultar(UUID id) {
        return repository.findById(id)
                .map(p -> modelMapper.map(p, PacienteResponse.class))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: " + id));
    }

    // UC - Atualizar paciente
    public PacienteResponse atualizar(UUID id, AtualizarPacienteRequest request) {
        var paciente = buscarOuLancarErro(id);

        if (request.email() != null && repository.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }

        modelMapper.map(request, paciente);

        return modelMapper.map(repository.save(paciente), PacienteResponse.class);
    }

    // UC - Remover (inativar) paciente
    public void remover(UUID id) {
        var paciente = buscarOuLancarErro(id);
        paciente.setAtivo(false);
        repository.save(paciente);
    }

    // UC - Listar pacientes
    @Transactional(readOnly = true)
    public List<PacienteResponse> listar(Boolean ativo) {
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
}
