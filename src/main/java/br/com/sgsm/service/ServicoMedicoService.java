package br.com.sgsm.service;

import br.com.sgsm.domain.ServicoMedico;
import br.com.sgsm.dto.AtualizarServicoMedicoRequest;
import br.com.sgsm.dto.CadastrarServicoMedicoRequest;
import br.com.sgsm.dto.ServicoMedicoResponse;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.ServicoMedicoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ServicoMedicoService {

    private final ServicoMedicoRepository repository;
    private final ModelMapper modelMapper;
    private final VetorizacaoPublisher vetorizacaoPublisher;

    public ServicoMedicoService(ServicoMedicoRepository repository, ModelMapper modelMapper,
                                VetorizacaoPublisher vetorizacaoPublisher) {
        this.repository = repository;
        this.modelMapper = modelMapper;
        this.vetorizacaoPublisher = vetorizacaoPublisher;
    }

    public ServicoMedicoResponse cadastrar(CadastrarServicoMedicoRequest request) {
        var servico = modelMapper.map(request, ServicoMedico.class);
        var salvo = repository.save(servico);
        vetorizacaoPublisher.publicar("SERVICO_MEDICO", salvo.getId().toString(), "CREATE");
        return modelMapper.map(salvo, ServicoMedicoResponse.class);
    }

    @Transactional(readOnly = true)
    public ServicoMedicoResponse consultar(UUID id) {
        return repository.findById(id)
                .map(s -> modelMapper.map(s, ServicoMedicoResponse.class))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço médico não encontrado: " + id));
    }

    public ServicoMedicoResponse atualizar(UUID id, AtualizarServicoMedicoRequest request) {
        var servico = buscarOuLancarErro(id);
        modelMapper.map(request, servico);
        var salvo = repository.save(servico);
        vetorizacaoPublisher.publicar("SERVICO_MEDICO", salvo.getId().toString(), "UPDATE");
        return modelMapper.map(salvo, ServicoMedicoResponse.class);
    }

    public void remover(UUID id) {
        var servico = buscarOuLancarErro(id);
        servico.setAtivo(false);
        repository.save(servico);
    }

    @Transactional(readOnly = true)
    public List<ServicoMedicoResponse> listar(Boolean ativo, UUID medicoId) {
        List<ServicoMedico> resultado;

        if (medicoId != null && ativo != null) {
            resultado = repository.findAllByMedicoIdAndAtivo(medicoId, ativo);
        } else if (medicoId != null) {
            resultado = repository.findAllByMedicoId(medicoId);
        } else if (ativo != null) {
            resultado = repository.findAllByAtivo(ativo);
        } else {
            resultado = repository.findAll();
        }

        return resultado.stream()
                .map(s -> modelMapper.map(s, ServicoMedicoResponse.class))
                .toList();
    }

    private ServicoMedico buscarOuLancarErro(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço médico não encontrado: " + id));
    }
}
