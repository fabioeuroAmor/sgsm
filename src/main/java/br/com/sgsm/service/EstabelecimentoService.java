package br.com.sgsm.service;

import br.com.sgsm.domain.Estabelecimento;
import br.com.sgsm.domain.MedicoEstabelecimento;
import br.com.sgsm.dto.*;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.MedicoEstabelecimentoRepository;
import br.com.sgsm.repository.MedicoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EstabelecimentoService {

    private final EstabelecimentoRepository repository;
    private final MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    private final MedicoRepository medicoRepository;
    private final ModelMapper modelMapper;
    private final VetorizacaoPublisher vetorizacaoPublisher;

    public EstabelecimentoService(
            EstabelecimentoRepository repository,
            MedicoEstabelecimentoRepository medicoEstabelecimentoRepository,
            MedicoRepository medicoRepository,
            ModelMapper modelMapper,
            VetorizacaoPublisher vetorizacaoPublisher) {
        this.repository = repository;
        this.medicoEstabelecimentoRepository = medicoEstabelecimentoRepository;
        this.medicoRepository = medicoRepository;
        this.modelMapper = modelMapper;
        this.vetorizacaoPublisher = vetorizacaoPublisher;
    }

    public EstabelecimentoResponse cadastrar(CadastrarEstabelecimentoRequest request) {
        if (repository.existsByCnpj(request.cnpj())) {
            throw new IllegalArgumentException("CNPJ já cadastrado: " + request.cnpj());
        }

        var estabelecimento = modelMapper.map(request, Estabelecimento.class);
        estabelecimento.setUf(estabelecimento.getUf().toUpperCase());
        var salvo = repository.save(estabelecimento);
        vetorizacaoPublisher.publicar("ESTABELECIMENTO", salvo.getId().toString(), "CREATE");
        return modelMapper.map(salvo, EstabelecimentoResponse.class);
    }

    @Transactional(readOnly = true)
    public EstabelecimentoResponse consultar(UUID id) {
        return repository.findById(id)
                .map(e -> modelMapper.map(e, EstabelecimentoResponse.class))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + id));
    }

    public EstabelecimentoResponse atualizar(UUID id, AtualizarEstabelecimentoRequest request) {
        var estabelecimento = buscarOuLancarErro(id);
        modelMapper.map(request, estabelecimento);

        if (estabelecimento.getUf() != null) {
            estabelecimento.setUf(estabelecimento.getUf().toUpperCase());
        }

        var salvo = repository.save(estabelecimento);
        vetorizacaoPublisher.publicar("ESTABELECIMENTO", salvo.getId().toString(), "UPDATE");
        return modelMapper.map(salvo, EstabelecimentoResponse.class);
    }

    public void remover(UUID id) {
        var estabelecimento = buscarOuLancarErro(id);
        estabelecimento.setAtivo(false);
        repository.save(estabelecimento);
    }

    @Transactional(readOnly = true)
    public List<EstabelecimentoResponse> listar(Boolean ativo, String uf, String cidade) {
        return listar(ativo, uf, cidade, null);
    }

    @Transactional(readOnly = true)
    public List<EstabelecimentoResponse> listar(Boolean ativo, String uf, String cidade, UUID medicoId) {
        List<Estabelecimento> resultado;

        if (medicoId != null) {
            List<UUID> ids = medicoEstabelecimentoRepository
                    .findById_MedicoIdAndAtivo(medicoId, true)
                    .stream()
                    .map(me -> me.getId().getEstabelecimentoId())
                    .toList();
            resultado = (List<Estabelecimento>) repository.findAllById(ids);
            if (ativo != null) {
                final Boolean filtroAtivo = ativo;
                resultado = resultado.stream().filter(e -> filtroAtivo.equals(e.getAtivo())).toList();
            }
        } else if (uf != null && cidade != null && ativo != null) {
            resultado = repository.findAllByUfAndCidadeAndAtivo(uf.toUpperCase(), cidade, ativo);
        } else if (uf != null && cidade != null) {
            resultado = repository.findAllByUfAndCidade(uf.toUpperCase(), cidade);
        } else if (uf != null && ativo != null) {
            resultado = repository.findAllByUfAndAtivo(uf.toUpperCase(), ativo);
        } else if (cidade != null && ativo != null) {
            resultado = repository.findAllByCidadeAndAtivo(cidade, ativo);
        } else if (uf != null) {
            resultado = repository.findAllByUf(uf.toUpperCase());
        } else if (cidade != null) {
            resultado = repository.findAllByCidade(cidade);
        } else if (ativo != null) {
            resultado = repository.findAllByAtivo(ativo);
        } else {
            resultado = repository.findAll();
        }

        return resultado.stream()
                .map(e -> modelMapper.map(e, EstabelecimentoResponse.class))
                .toList();
    }

    // UC - Listar médicos vinculados ao estabelecimento
    @Transactional(readOnly = true)
    public List<MedicoResponse> listarMedicos(UUID estabelecimentoId) {
        buscarOuLancarErro(estabelecimentoId);
        return medicoEstabelecimentoRepository
                .findById_EstabelecimentoIdAndAtivo(estabelecimentoId, true)
                .stream()
                .map(me -> medicoRepository.findById(me.getId().getMedicoId()).orElse(null))
                .filter(m -> m != null && Boolean.TRUE.equals(m.getAtivo()))
                .map(m -> modelMapper.map(m, MedicoResponse.class))
                .toList();
    }

    // UC - Associar médicos ao estabelecimento (substitui toda a lista)
    public void associarMedicos(UUID estabelecimentoId, AssociarMedicosRequest request) {
        buscarOuLancarErro(estabelecimentoId);

        request.medicoIds().forEach(medicoId ->
            medicoRepository.findById(medicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Médico não encontrado: " + medicoId))
        );

        medicoEstabelecimentoRepository.deleteByEstabelecimentoId(estabelecimentoId);

        request.medicoIds().forEach(medicoId ->
            medicoEstabelecimentoRepository.save(new MedicoEstabelecimento(medicoId, estabelecimentoId))
        );
    }

    private Estabelecimento buscarOuLancarErro(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + id));
    }
}
