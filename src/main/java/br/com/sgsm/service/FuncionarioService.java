package br.com.sgsm.service;

import br.com.sgsm.domain.Funcionario;
import br.com.sgsm.domain.MedicoEstabelecimento;
import br.com.sgsm.dto.AtualizarFuncionarioRequest;
import br.com.sgsm.dto.CadastrarFuncionarioRequest;
import br.com.sgsm.dto.FuncionarioResponse;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.FuncionarioRepository;
import br.com.sgsm.repository.MedicoEstabelecimentoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FuncionarioService {

    private final FuncionarioRepository repository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    private final ModelMapper modelMapper;
    private final ContextoSeguranca contextoSeguranca;

    public FuncionarioService(FuncionarioRepository repository,
                              EstabelecimentoRepository estabelecimentoRepository,
                              MedicoEstabelecimentoRepository medicoEstabelecimentoRepository,
                              ModelMapper modelMapper,
                              ContextoSeguranca contextoSeguranca) {
        this.repository = repository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.medicoEstabelecimentoRepository = medicoEstabelecimentoRepository;
        this.modelMapper = modelMapper;
        this.contextoSeguranca = contextoSeguranca;
    }

    public FuncionarioResponse cadastrar(CadastrarFuncionarioRequest request) {
        if (!estabelecimentoRepository.existsById(request.estabelecimentoId())) {
            throw new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + request.estabelecimentoId());
        }
        if (contextoSeguranca.isMedico()) {
            validarEstabelecimentoDoMedico(request.estabelecimentoId());
        }
        if (repository.existsByCpf(request.cpf())) {
            throw new IllegalArgumentException("CPF já cadastrado: " + request.cpf());
        }

        var funcionario = modelMapper.map(request, Funcionario.class);
        return modelMapper.map(repository.save(funcionario), FuncionarioResponse.class);
    }

    @Transactional(readOnly = true)
    public FuncionarioResponse consultar(UUID id) {
        var funcionario = buscarOuLancarErro(id);
        if (contextoSeguranca.isMedico()) {
            validarEstabelecimentoDoMedico(funcionario.getEstabelecimentoId());
        }
        return modelMapper.map(funcionario, FuncionarioResponse.class);
    }

    @Transactional(readOnly = true)
    public List<FuncionarioResponse> listar(UUID estabelecimentoId, Boolean ativo) {
        List<Funcionario> resultado;

        if (contextoSeguranca.isMedico()) {
            List<UUID> estabelecimentosDoMedico = medicoEstabelecimentoRepository
                    .findById_MedicoIdAndAtivo(contextoSeguranca.getReferenciaId(), true)
                    .stream()
                    .map(me -> me.getId().getEstabelecimentoId())
                    .toList();

            if (estabelecimentoId != null) {
                if (!estabelecimentosDoMedico.contains(estabelecimentoId)) {
                    throw new AcessoNegadoException("Acesso negado ao estabelecimento: " + estabelecimentoId);
                }
                resultado = ativo != null
                        ? repository.findAllByEstabelecimentoIdAndAtivo(estabelecimentoId, ativo)
                        : repository.findAllByEstabelecimentoId(estabelecimentoId);
            } else {
                resultado = ativo != null
                        ? repository.findAllByEstabelecimentoIdInAndAtivo(estabelecimentosDoMedico, ativo)
                        : repository.findAllByEstabelecimentoIdIn(estabelecimentosDoMedico);
            }
        } else {
            if (estabelecimentoId != null) {
                resultado = ativo != null
                        ? repository.findAllByEstabelecimentoIdAndAtivo(estabelecimentoId, ativo)
                        : repository.findAllByEstabelecimentoId(estabelecimentoId);
            } else {
                resultado = ativo != null
                        ? repository.findAll().stream().filter(f -> f.getAtivo().equals(ativo)).toList()
                        : repository.findAll();
            }
        }

        return resultado.stream()
                .map(f -> modelMapper.map(f, FuncionarioResponse.class))
                .toList();
    }

    public FuncionarioResponse atualizar(UUID id, AtualizarFuncionarioRequest request) {
        var funcionario = buscarOuLancarErro(id);
        if (contextoSeguranca.isMedico()) {
            validarEstabelecimentoDoMedico(funcionario.getEstabelecimentoId());
        }
        if (request.email() != null && repository.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }
        modelMapper.map(request, funcionario);
        return modelMapper.map(repository.save(funcionario), FuncionarioResponse.class);
    }

    public void remover(UUID id) {
        var funcionario = buscarOuLancarErro(id);
        if (contextoSeguranca.isMedico()) {
            validarEstabelecimentoDoMedico(funcionario.getEstabelecimentoId());
        }
        funcionario.setAtivo(false);
        repository.save(funcionario);
    }

    private Funcionario buscarOuLancarErro(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionário não encontrado: " + id));
    }

    private void validarEstabelecimentoDoMedico(UUID estabelecimentoId) {
        boolean vinculado = medicoEstabelecimentoRepository
                .findById_MedicoIdAndAtivo(contextoSeguranca.getReferenciaId(), true)
                .stream()
                .anyMatch(me -> me.getId().getEstabelecimentoId().equals(estabelecimentoId));
        if (!vinculado) {
            throw new AcessoNegadoException("Estabelecimento não vinculado ao médico: " + estabelecimentoId);
        }
    }
}
