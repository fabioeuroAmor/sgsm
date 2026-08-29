package br.com.sgsm.service;

import br.com.sgsm.domain.AcaoAuditoria;
import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.Paciente;
import br.com.sgsm.dto.AtualizarPacienteRequest;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.dto.PacienteExportacaoResponse;
import br.com.sgsm.dto.PacienteResponse;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.AgendamentoRepository;
import br.com.sgsm.repository.PacienteRepository;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.CpfCryptoService;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private final AuditoriaService auditoriaService;
    private final CpfCryptoService cpfCryptoService;
    private final AgendamentoService agendamentoService;

    public PacienteService(PacienteRepository repository,
                           AgendamentoRepository agendamentoRepository,
                           ModelMapper modelMapper,
                           ContextoSeguranca contextoSeguranca,
                           VetorizacaoPublisher vetorizacaoPublisher,
                           AuditoriaService auditoriaService,
                           CpfCryptoService cpfCryptoService,
                           AgendamentoService agendamentoService) {
        this.repository = repository;
        this.agendamentoRepository = agendamentoRepository;
        this.modelMapper = modelMapper;
        this.contextoSeguranca = contextoSeguranca;
        this.vetorizacaoPublisher = vetorizacaoPublisher;
        this.auditoriaService = auditoriaService;
        this.cpfCryptoService = cpfCryptoService;
        this.agendamentoService = agendamentoService;
    }

    // UC - Cadastrar paciente
    public PacienteResponse cadastrar(CadastrarPacienteRequest request) {
        if (!cpfValido(request.cpf())) {
            throw new IllegalArgumentException("CPF inválido: " + request.cpf());
        }
        if (!Boolean.TRUE.equals(request.consentimentoLgpd())) {
            throw new IllegalArgumentException("Consentimento com o tratamento de dados (LGPD) é obrigatório para o cadastro.");
        }
        String cpfHash = cpfCryptoService.hash(normalizarCpf(request.cpf()));
        if (repository.existsByCpfHash(cpfHash)) {
            throw new IllegalArgumentException("CPF já cadastrado: " + request.cpf());
        }
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + request.email());
        }

        var paciente = modelMapper.map(request, Paciente.class);
        paciente.setCpfHash(cpfHash);
        paciente.setConsentimentoLgpdEm(OffsetDateTime.now());
        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "CREATE");
        auditoriaService.registrar("PACIENTE", salvo.getId(), AcaoAuditoria.CRIACAO);
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
        var response = repository.findById(id)
                .map(p -> modelMapper.map(p, PacienteResponse.class))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: " + id));
        auditoriaService.registrar("PACIENTE", id, AcaoAuditoria.LEITURA);
        return response;
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
        auditoriaService.registrar("PACIENTE", id, AcaoAuditoria.ATUALIZACAO);
        return modelMapper.map(salvo, PacienteResponse.class);
    }

    // UC - Remover (inativar) paciente
    public void remover(UUID id) {
        var paciente = buscarOuLancarErro(id);
        paciente.setAtivo(false);
        // Base de contagem da retenção de 20 anos (POLITICA_RETENCAO_DADOS.md) usada
        // pelo job de expurgo — nulo enquanto ativo, setado no momento do encerramento.
        paciente.setEncerradoEm(OffsetDateTime.now());
        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "UPDATE");
        auditoriaService.registrar("PACIENTE", id, AcaoAuditoria.INATIVACAO);
    }

    // UC - Reativar paciente
    public PacienteResponse reativar(UUID id) {
        var paciente = buscarOuLancarErro(id);
        if (Boolean.TRUE.equals(paciente.getAnonimizado())) {
            throw new IllegalStateException("Paciente anonimizado não pode ser reativado: " + id);
        }
        paciente.setAtivo(true);
        paciente.setEncerradoEm(null);
        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "UPDATE");
        auditoriaService.registrar("PACIENTE", id, AcaoAuditoria.REATIVACAO);
        return modelMapper.map(salvo, PacienteResponse.class);
    }

    // UC - Exportar dados do paciente (LGPD 3.2 — portabilidade, art. 18 da LGPD)
    @Transactional(readOnly = true)
    public PacienteExportacaoResponse exportar(UUID id) {
        UUID ref = contextoSeguranca.getReferenciaId();
        boolean ehProprioTitular = contextoSeguranca.isPaciente() && id.equals(ref);
        if (!ehProprioTitular && !contextoSeguranca.isDesenvolvedor()) {
            throw new AcessoNegadoException("Exportação de dados é restrita ao próprio titular ou a perfil administrativo: " + id);
        }
        var paciente = repository.findById(id)
                .map(p -> modelMapper.map(p, PacienteResponse.class))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: " + id));
        var agendamentos = agendamentoService.listar(id, null, null);
        auditoriaService.registrar("PACIENTE", id, AcaoAuditoria.EXPORTACAO);
        return new PacienteExportacaoResponse(paciente, agendamentos);
    }

    // UC - Anonimizar paciente (LGPD 3.3 — direito ao esquecimento, art. 18 da LGPD)
    public void anonimizar(UUID id) {
        var paciente = buscarOuLancarErro(id);
        if (Boolean.TRUE.equals(paciente.getAnonimizado())) {
            throw new IllegalStateException("Paciente já anonimizado: " + id);
        }

        // Mantém só o necessário para fins estatísticos/legais (POLITICA_RETENCAO_DADOS.md):
        // nome/CPF/e-mail/telefone/endereço zerados; ano de nascimento preservado.
        paciente.setNome("ANONIMIZADO");
        paciente.setCpf("00000000000");
        paciente.setCpfHash(null);
        paciente.setEmail("anonimizado-" + id + "@sgsm.invalid");
        paciente.setTelefone(null);
        paciente.setLogradouro(null);
        paciente.setNumero(null);
        paciente.setComplemento(null);
        paciente.setBairro(null);
        paciente.setCidade(null);
        paciente.setUf(null);
        paciente.setCep(null);
        if (paciente.getDataNascimento() != null) {
            paciente.setDataNascimento(LocalDate.of(paciente.getDataNascimento().getYear(), 1, 1));
        }
        paciente.setAtivo(false);
        paciente.setAnonimizado(true);
        if (paciente.getEncerradoEm() == null) {
            paciente.setEncerradoEm(OffsetDateTime.now());
        }

        var salvo = repository.save(paciente);
        vetorizacaoPublisher.publicar("PACIENTE", salvo.getId().toString(), "ANONIMIZAR");
        auditoriaService.registrar("PACIENTE", id, AcaoAuditoria.ANONIMIZACAO);
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

    private String normalizarCpf(String cpf) {
        return cpf == null ? null : cpf.replaceAll("\\D", "");
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
