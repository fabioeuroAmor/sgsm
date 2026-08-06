package br.com.sgsm.service;

import br.com.sgsm.domain.Medico;
import br.com.sgsm.dto.AtualizarMedicoRequest;
import br.com.sgsm.dto.CadastrarMedicoRequest;
import br.com.sgsm.dto.MedicoResponse;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.MedicoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MedicoService {

    private final MedicoRepository repository;
    private final ModelMapper modelMapper;
    private final ContextoSeguranca contextoSeguranca;
    private final VetorizacaoPublisher vetorizacaoPublisher;

    public MedicoService(MedicoRepository repository, ModelMapper modelMapper,
                         ContextoSeguranca contextoSeguranca, VetorizacaoPublisher vetorizacaoPublisher) {
        this.repository = repository;
        this.modelMapper = modelMapper;
        this.contextoSeguranca = contextoSeguranca;
        this.vetorizacaoPublisher = vetorizacaoPublisher;
    }

    public MedicoResponse cadastrar(CadastrarMedicoRequest request) {
        if (repository.existsByCrmAndCrmUf(request.crm(), request.crmUf())) {
            throw new IllegalArgumentException(
                    "CRM %s/%s já cadastrado.".formatted(request.crm(), request.crmUf()));
        }
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }
        if (repository.existsByTelefone(request.telefone())) {
            throw new IllegalStateException("Telefone já cadastrado: " + request.telefone());
        }

        var medico = modelMapper.map(request, Medico.class);
        medico.setCrmUf(medico.getCrmUf().toUpperCase());
        var salvo = repository.save(medico);
        vetorizacaoPublisher.publicar("MEDICO", salvo.getId().toString(), "CREATE");
        return modelMapper.map(salvo, MedicoResponse.class);
    }

    @Transactional(readOnly = true)
    public MedicoResponse consultar(UUID id) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isMedico() && !id.equals(ref)) {
            throw new AcessoNegadoException("Acesso negado ao médico: " + id);
        }
        return repository.findById(id)
                .map(m -> modelMapper.map(m, MedicoResponse.class))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Médico não encontrado: " + id));
    }

    public MedicoResponse atualizar(UUID id, AtualizarMedicoRequest request) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isMedico() && !id.equals(ref)) {
            throw new AcessoNegadoException("Médico não pode editar dados de outro médico: " + id);
        }
        var medico = buscarOuLancarErro(id);

        if (request.email() != null && repository.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }
        if (request.telefone() != null && repository.existsByTelefoneAndIdNot(request.telefone(), id)) {
            throw new IllegalStateException("Telefone já cadastrado: " + request.telefone());
        }

        modelMapper.map(request, medico);
        var salvo = repository.save(medico);
        vetorizacaoPublisher.publicar("MEDICO", salvo.getId().toString(), "UPDATE");
        return modelMapper.map(salvo, MedicoResponse.class);
    }

    public void remover(UUID id) {
        var medico = buscarOuLancarErro(id);
        medico.setAtivo(false);
        repository.save(medico);
    }

    @Transactional(readOnly = true)
    public List<MedicoResponse> listar(Boolean ativo, String especialidade) {
        if (contextoSeguranca.isMedico()) {
            return repository.findById(contextoSeguranca.getReferenciaId())
                    .map(m -> List.of(modelMapper.map(m, MedicoResponse.class)))
                    .orElse(List.of());
        }

        List<Medico> resultado;

        if (especialidade != null && ativo != null) {
            resultado = repository.findAllByEspecialidadeAndAtivo(especialidade, ativo);
        } else if (especialidade != null) {
            resultado = repository.findAllByEspecialidade(especialidade);
        } else if (ativo != null) {
            resultado = repository.findAllByAtivo(ativo);
        } else {
            resultado = repository.findAll();
        }

        return resultado.stream()
                .map(m -> modelMapper.map(m, MedicoResponse.class))
                .toList();
    }

    private Medico buscarOuLancarErro(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Médico não encontrado: " + id));
    }
}
