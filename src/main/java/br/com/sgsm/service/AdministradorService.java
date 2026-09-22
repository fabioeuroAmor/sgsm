package br.com.sgsm.service;

import br.com.sgsm.domain.Administrador;
import br.com.sgsm.domain.AdministradorEstabelecimento;
import br.com.sgsm.dto.*;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.AdministradorEstabelecimentoRepository;
import br.com.sgsm.repository.AdministradorRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Acesso a este service e 100% gated por rota (DESENVOLVEDOR-only, ver SecurityConfig)
// - nao ha checagem de ContextoSeguranca aqui, diferente de FuncionarioService.
@Service
@Transactional
public class AdministradorService {

    private final AdministradorRepository repository;
    private final AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final ModelMapper modelMapper;

    public AdministradorService(AdministradorRepository repository,
                                AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository,
                                EstabelecimentoRepository estabelecimentoRepository,
                                ModelMapper modelMapper) {
        this.repository = repository;
        this.administradorEstabelecimentoRepository = administradorEstabelecimentoRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.modelMapper = modelMapper;
    }

    public AdministradorResponse cadastrar(CadastrarAdministradorRequest request) {
        if (repository.existsByCpf(request.cpf())) {
            throw new IllegalArgumentException("CPF já cadastrado: " + request.cpf());
        }
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }

        var administrador = modelMapper.map(request, Administrador.class);
        return modelMapper.map(repository.save(administrador), AdministradorResponse.class);
    }

    @Transactional(readOnly = true)
    public AdministradorResponse consultar(UUID id) {
        return modelMapper.map(buscarOuLancarErro(id), AdministradorResponse.class);
    }

    @Transactional(readOnly = true)
    public List<AdministradorResponse> listar(Boolean ativo) {
        List<Administrador> resultado = (ativo != null)
                ? repository.findAllByAtivo(ativo)
                : repository.findAll();
        return resultado.stream()
                .map(a -> modelMapper.map(a, AdministradorResponse.class))
                .toList();
    }

    public AdministradorResponse atualizar(UUID id, AtualizarAdministradorRequest request) {
        var administrador = buscarOuLancarErro(id);
        if (request.email() != null && repository.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }
        modelMapper.map(request, administrador);
        return modelMapper.map(repository.save(administrador), AdministradorResponse.class);
    }

    public void remover(UUID id) {
        var administrador = buscarOuLancarErro(id);
        administrador.setAtivo(false);
        repository.save(administrador);
    }

    public AdministradorResponse reativar(UUID id) {
        var administrador = buscarOuLancarErro(id);
        administrador.setAtivo(true);
        return modelMapper.map(repository.save(administrador), AdministradorResponse.class);
    }

    // UC - Listar estabelecimentos administrados
    @Transactional(readOnly = true)
    public List<EstabelecimentoResponse> listarEstabelecimentos(UUID administradorId) {
        buscarOuLancarErro(administradorId);
        return administradorEstabelecimentoRepository
                .findById_AdministradorIdAndAtivo(administradorId, true)
                .stream()
                .map(ae -> estabelecimentoRepository.findById(ae.getId().getEstabelecimentoId()).orElse(null))
                .filter(e -> e != null && Boolean.TRUE.equals(e.getAtivo()))
                .map(e -> modelMapper.map(e, EstabelecimentoResponse.class))
                .toList();
    }

    // UC - Vincular administrador a estabelecimento(s) (substitui toda a lista)
    public void vincularEstabelecimentos(UUID administradorId, AssociarEstabelecimentosRequest request) {
        buscarOuLancarErro(administradorId);

        request.estabelecimentoIds().forEach(estabelecimentoId ->
            estabelecimentoRepository.findById(estabelecimentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + estabelecimentoId))
        );

        administradorEstabelecimentoRepository.deleteByAdministradorId(administradorId);

        request.estabelecimentoIds().forEach(estabelecimentoId ->
            administradorEstabelecimentoRepository.save(new AdministradorEstabelecimento(administradorId, estabelecimentoId))
        );
    }

    private Administrador buscarOuLancarErro(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Administrador não encontrado: " + id));
    }
}
