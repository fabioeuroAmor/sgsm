package br.com.sgsm.service;

import br.com.sgsm.domain.Medico;
import br.com.sgsm.domain.MedicoEstabelecimento;
import br.com.sgsm.dto.AtualizarMedicoRequest;
import br.com.sgsm.dto.CadastrarMedicoRequest;
import br.com.sgsm.dto.MedicoResponse;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.MedicoEstabelecimentoRepository;
import br.com.sgsm.repository.MedicoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MedicoService {

    private final MedicoRepository repository;
    private final ModelMapper modelMapper;
    private final ContextoSeguranca contextoSeguranca;
    private final VetorizacaoPublisher vetorizacaoPublisher;
    private final EstabelecimentoAcessoService estabelecimentoAcessoService;
    private final MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;

    public MedicoService(MedicoRepository repository, ModelMapper modelMapper,
                         ContextoSeguranca contextoSeguranca, VetorizacaoPublisher vetorizacaoPublisher,
                         EstabelecimentoAcessoService estabelecimentoAcessoService,
                         MedicoEstabelecimentoRepository medicoEstabelecimentoRepository) {
        this.repository = repository;
        this.modelMapper = modelMapper;
        this.contextoSeguranca = contextoSeguranca;
        this.vetorizacaoPublisher = vetorizacaoPublisher;
        this.estabelecimentoAcessoService = estabelecimentoAcessoService;
        this.medicoEstabelecimentoRepository = medicoEstabelecimentoRepository;
    }

    public MedicoResponse cadastrar(CadastrarMedicoRequest request) {
        if (repository.existsByCrmAndCrmUf(request.crm(), request.crmUf())) {
            throw new IllegalArgumentException(
                    "CRM %s/%s já cadastrado.".formatted(request.crm(), request.crmUf()));
        }
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }
        UUID clinica = clinicaDoNovoMedico(request.estabelecimentoId());

        var medico = modelMapper.map(request, Medico.class);
        medico.setCrmUf(medico.getCrmUf().toUpperCase());
        var salvo = repository.save(medico);
        if (clinica != null) {
            medicoEstabelecimentoRepository.save(new MedicoEstabelecimento(salvo.getId(), clinica));
        }
        vetorizacaoPublisher.publicar("MEDICO", salvo.getId().toString(), "CREATE");
        return modelMapper.map(salvo, MedicoResponse.class);
    }

    // Médico cadastrado por FUNCIONARIO/ADMIN_ESTABELECIMENTO nasce vinculado à clínica de quem
    // cadastra: sem isso ele sumia da lista de quem o criou (escopo por clínica) e ficava sem convite.
    // DESENVOLVEDOR (irrestrito) vincula só se informar a clínica.
    private UUID clinicaDoNovoMedico(UUID informada) {
        List<UUID> permitidas = estabelecimentoAcessoService.estabelecimentosPermitidos();
        if (permitidas == null) {
            return informada;
        }
        if (informada != null) {
            if (!permitidas.contains(informada)) {
                throw new AcessoNegadoException("Acesso negado ao estabelecimento: " + informada);
            }
            return informada;
        }
        if (permitidas.size() == 1) {
            return permitidas.get(0);
        }
        if (permitidas.isEmpty()) {
            throw new AcessoNegadoException("Seu usuário não está vinculado a nenhum estabelecimento.");
        }
        throw new IllegalArgumentException("Informe o estabelecimento em que o médico vai atender.");
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

        modelMapper.map(request, medico);
        var salvo = repository.save(medico);
        vetorizacaoPublisher.publicar("MEDICO", salvo.getId().toString(), "UPDATE");
        return modelMapper.map(salvo, MedicoResponse.class);
    }

    public void remover(UUID id) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isMedico() && !id.equals(ref)) {
            throw new AcessoNegadoException("Médico não pode inativar outro médico: " + id);
        }
        var medico = buscarOuLancarErro(id);
        medico.setAtivo(false);
        var salvo = repository.save(medico);
        vetorizacaoPublisher.publicar("MEDICO", salvo.getId().toString(), "UPDATE");
    }

    public MedicoResponse reativar(UUID id) {
        UUID ref = contextoSeguranca.getReferenciaId();
        if (contextoSeguranca.isMedico() && !id.equals(ref)) {
            throw new AcessoNegadoException("Médico não pode reativar outro médico: " + id);
        }
        var medico = buscarOuLancarErro(id);
        medico.setAtivo(true);
        var salvo = repository.save(medico);
        vetorizacaoPublisher.publicar("MEDICO", salvo.getId().toString(), "UPDATE");
        return modelMapper.map(salvo, MedicoResponse.class);
    }

    @Transactional(readOnly = true)
    public List<MedicoResponse> listar(Boolean ativo, String especialidade) {
        List<Medico> resultado;
        List<UUID> medicosPermitidos = estabelecimentoAcessoService.medicosPermitidos();

        // o médico sempre vê o próprio cadastro, mesmo ainda sem clínica vinculada
        if (medicosPermitidos != null && contextoSeguranca.isMedico()
                && contextoSeguranca.getReferenciaId() != null
                && !medicosPermitidos.contains(contextoSeguranca.getReferenciaId())) {
            medicosPermitidos = new ArrayList<>(medicosPermitidos);
            medicosPermitidos.add(contextoSeguranca.getReferenciaId());
        }

        if (medicosPermitidos != null) {
            resultado = medicosPermitidos.isEmpty() ? List.of() : (List<Medico>) repository.findAllById(medicosPermitidos);
            if (especialidade != null) {
                resultado = resultado.stream().filter(m -> especialidade.equals(m.getEspecialidade())).toList();
            }
            if (ativo != null) {
                final Boolean filtroAtivo = ativo;
                resultado = resultado.stream().filter(m -> filtroAtivo.equals(m.getAtivo())).toList();
            }
        } else if (especialidade != null && ativo != null) {
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
