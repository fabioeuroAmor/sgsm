package br.com.sgsm.service;

import br.com.sgsm.domain.ServicoMedico;
import br.com.sgsm.dto.AtualizarServicoMedicoRequest;
import br.com.sgsm.dto.CadastrarServicoMedicoRequest;
import br.com.sgsm.dto.ServicoMedicoResponse;
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

    public ServicoMedicoService(ServicoMedicoRepository repository, ModelMapper modelMapper) {
        this.repository = repository;
        this.modelMapper = modelMapper;
    }

    public ServicoMedicoResponse cadastrar(CadastrarServicoMedicoRequest request) {
        var servico = modelMapper.map(request, ServicoMedico.class);
        return modelMapper.map(repository.save(servico), ServicoMedicoResponse.class);
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
        return modelMapper.map(repository.save(servico), ServicoMedicoResponse.class);
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
