package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.AcaoAuditoria;
import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.Paciente;
import br.com.sgsm.domain.PacienteEstabelecimento;
import br.com.sgsm.dto.AtualizarPacienteRequest;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.AgendamentoRepository;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.repository.PacienteRepository;
import org.springframework.test.util.ReflectionTestUtils;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.CpfCryptoService;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository repository;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private ContextoSeguranca contextoSeguranca;
    @Mock
    private VetorizacaoPublisher vetorizacaoPublisher;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private AgendamentoService agendamentoService;
    @Mock
    private EstabelecimentoAcessoService estabelecimentoAcessoService;
    @Mock
    private PacienteClinicaService pacienteClinicaService;

    private CpfCryptoService cpfCryptoService;
    private PacienteService service;

    @BeforeEach
    void setUp() {
        String chaveAes = Base64.getEncoder().encodeToString("chave-teste-de-32-bytes-exatos!!".getBytes());
        String chaveHmac = Base64.getEncoder().encodeToString("outra-chave-de-32-bytes-teste!!!".getBytes());
        cpfCryptoService = new CpfCryptoService(chaveAes, chaveHmac);
        service = new PacienteService(repository, agendamentoRepository,
                new ModelMapperConfig().modelMapper(), contextoSeguranca, vetorizacaoPublisher, auditoriaService,
                cpfCryptoService, agendamentoService, estabelecimentoAcessoService, pacienteClinicaService);
        // Mockito retorna List vazia (nao null) para metodo List<> nao-stubado - sem isso,
        // o service entenderia "escopado a zero medicos" em vez de "irrestrito".
        lenient().when(estabelecimentoAcessoService.medicosPermitidos()).thenReturn(null);
    }

    @Test
    void cpfOuEmailJaCadastradoConsultaPorHashDoCpfEPorEmail() {
        String hash = cpfCryptoService.hash("52998224725");
        when(repository.existsByCpfHash(hash)).thenReturn(false, true);
        when(repository.existsByEmail("a@b.com")).thenReturn(true);
        when(repository.existsByEmail("livre@b.com")).thenReturn(false);

        assertThat(service.cpfOuEmailJaCadastrado("529.982.247-25", "livre@b.com")).isFalse();
        assertThat(service.cpfOuEmailJaCadastrado("529.982.247-25", "livre@b.com")).isTrue();
        assertThat(service.cpfOuEmailJaCadastrado(null, "a@b.com")).isTrue();
        assertThat(service.cpfOuEmailJaCadastrado(null, null)).isFalse();
    }

    @Test
    void removerAutoCadastroIncompletoApagaEPedeRemocaoDoVetor() {
        var id = java.util.UUID.randomUUID();

        service.removerAutoCadastroIncompleto(id);

        verify(repository).deleteById(id);
        verify(vetorizacaoPublisher).publicar("PACIENTE", id.toString(), "ANONIMIZAR");
    }

    private Paciente novoPaciente() {
        var p = new Paciente();
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        p.setNome("João Silva");
        p.setCpf("12345678900");
        p.setDataNascimento(LocalDate.of(1990, 1, 1));
        p.setEmail("joao@sgsm.com.br");
        p.setAtivo(true);
        return p;
    }

    private Agendamento novoAgendamento(UUID pacienteId) {
        var a = new Agendamento();
        a.setPacienteId(pacienteId);
        return a;
    }

    @Test
    void deveCadastrarPacienteQuandoCpfEEmailInexistentes() {
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        var request = new CadastrarPacienteRequest("João Silva", "52998224725",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null, true);

        var response = service.cadastrar(request);

        assertThat(response.getNome()).isEqualTo("João Silva");
        verify(auditoriaService).registrar(eq("PACIENTE"), any(UUID.class), eq(AcaoAuditoria.CRIACAO));

        var captor = ArgumentCaptor.forClass(Paciente.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCpfHash()).isEqualTo(cpfCryptoService.hash("52998224725"));
    }

    @Test
    void deveLancarExcecaoQuandoCpfInvalido() {
        var request = new CadastrarPacienteRequest("João Silva", "11111111111",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null, true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF inválido");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoCpfJaCadastrado() {
        when(repository.existsByCpfHash(cpfCryptoService.hash("52998224725"))).thenReturn(true);
        var request = new CadastrarPacienteRequest("João Silva", "52998224725",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null, true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("52998224725");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoConsentimentoLgpdAusente() {
        var request = new CadastrarPacienteRequest("João Silva", "52998224725",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null, false);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Consentimento");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoEmailJaCadastrado() {
        when(repository.existsByEmail("joao@sgsm.com.br")).thenReturn(true);
        var request = new CadastrarPacienteRequest("João Silva", "52998224725",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null, true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("joao@sgsm.com.br");
    }

    @Test
    void deveConsultarPacienteQuandoNaoForPacienteNemMedico() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(novoPaciente()));

        var response = service.consultar(id);

        assertThat(response.getNome()).isEqualTo("João Silva");
        verify(auditoriaService).registrar("PACIENTE", id, AcaoAuditoria.LEITURA);
    }

    @Test
    void deveNegarAcessoQuandoPacienteConsultaOutroPaciente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveConsultarPacienteQuandoMedicoTemAgendamentoComEle() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(medicoId);
        when(agendamentoRepository.findAllByMedicoId(medicoId)).thenReturn(List.of(novoAgendamento(id)));
        when(repository.findById(id)).thenReturn(Optional.of(novoPaciente()));

        var response = service.consultar(id);

        assertThat(response).isNotNull();
    }

    @Test
    void deveNegarAcessoQuandoMedicoNaoTemAgendamentoComPaciente() {
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(medicoId);
        when(agendamentoRepository.findAllByMedicoId(medicoId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(AcessoNegadoException.class);
    }

    // QA WA-E02: a equipe de outra clínica não lê nem edita o paciente pelo id
    @Test
    void equipeSoConsultaPacienteAtendidoOuVinculadoNaSuaClinica() {
        UUID atendido = UUID.randomUUID();
        UUID vinculado = UUID.randomUUID();
        UUID deOutraClinica = UUID.randomUUID();
        UUID medicoDaClinica = UUID.randomUUID();
        UUID clinica = UUID.randomUUID();
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(estabelecimentoAcessoService.medicosPermitidos()).thenReturn(List.of(medicoDaClinica));
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(clinica));
        var consulta = novoAgendamento(atendido);
        consulta.setMedicoId(medicoDaClinica);
        when(agendamentoRepository.findAllByPacienteId(atendido)).thenReturn(List.of(consulta));
        when(agendamentoRepository.findAllByPacienteId(vinculado)).thenReturn(List.of());
        when(pacienteClinicaService.vinculosDoPaciente(vinculado))
                .thenReturn(List.of(new PacienteEstabelecimento(vinculado, clinica, "WHATSAPP")));
        var deOutroMedico = novoAgendamento(deOutraClinica);
        deOutroMedico.setMedicoId(UUID.randomUUID());
        when(agendamentoRepository.findAllByPacienteId(deOutraClinica)).thenReturn(List.of(deOutroMedico));
        when(pacienteClinicaService.vinculosDoPaciente(deOutraClinica))
                .thenReturn(List.of(new PacienteEstabelecimento(deOutraClinica, UUID.randomUUID(), "WHATSAPP")));
        when(repository.findById(any())).thenReturn(Optional.of(novoPaciente()));

        assertThat(service.consultar(atendido)).isNotNull();
        assertThat(service.consultar(vinculado)).isNotNull();
        assertThatThrownBy(() -> service.consultar(deOutraClinica)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.atualizar(deOutraClinica, null)).isInstanceOf(AcessoNegadoException.class);
        verify(auditoriaService, never()).registrar("PACIENTE", deOutraClinica, AcaoAuditoria.LEITURA);
    }

    @Test
    void adminSemClinicaNaoConsultaNinguem() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isAdminEstabelecimento()).thenReturn(true);
        when(estabelecimentoAcessoService.medicosPermitidos()).thenReturn(List.of());
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of());
        when(pacienteClinicaService.vinculosDoPaciente(id)).thenReturn(List.of());

        assertThatThrownBy(() -> service.consultar(id)).isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveLancarExcecaoAoConsultarPacienteInexistente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveAtualizarPacienteQuandoDadosValidos() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(novoPaciente()));
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        var request = new AtualizarPacienteRequest("João S. Silva", null, null, null, null, null, null, null, null, null, null);

        var response = service.atualizar(id, request);

        assertThat(response.getNome()).isEqualTo("João S. Silva");
        verify(auditoriaService).registrar("PACIENTE", id, AcaoAuditoria.ATUALIZACAO);
    }

    @Test
    void deveNegarAtualizacaoQuandoPacienteEditaOutroPaciente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());
        var request = new AtualizarPacienteRequest("x", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveLancarExcecaoAoAtualizarComEmailJaCadastradoPorOutroPaciente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(novoPaciente()));
        when(repository.existsByEmailAndIdNot("novo@sgsm.com.br", id)).thenReturn(true);
        var request = new AtualizarPacienteRequest(null, null, "novo@sgsm.com.br", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveInativarPacienteAoRemover() {
        UUID id = UUID.randomUUID();
        var paciente = novoPaciente();
        when(repository.findById(id)).thenReturn(Optional.of(paciente));
        when(repository.save(paciente)).thenReturn(paciente);

        service.remover(id);

        assertThat(paciente.getAtivo()).isFalse();
        assertThat(paciente.getEncerradoEm()).isNotNull();
        verify(repository).save(paciente);
        verify(auditoriaService).registrar("PACIENTE", id, AcaoAuditoria.INATIVACAO);
    }

    @Test
    void deveLancarExcecaoAoRemoverPacienteInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveReativarPaciente() {
        UUID id = UUID.randomUUID();
        var paciente = novoPaciente();
        paciente.setAtivo(false);
        paciente.setEncerradoEm(java.time.OffsetDateTime.now());
        when(repository.findById(id)).thenReturn(Optional.of(paciente));
        when(repository.save(paciente)).thenReturn(paciente);

        var response = service.reativar(id);

        assertThat(paciente.getAtivo()).isTrue();
        assertThat(paciente.getEncerradoEm()).isNull();
        assertThat(response.getAtivo()).isTrue();
        verify(repository).save(paciente);
        verify(auditoriaService).registrar("PACIENTE", id, AcaoAuditoria.REATIVACAO);
    }

    @Test
    void deveLancarExcecaoAoReativarPacienteInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reativar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoAoReativarPacienteAnonimizado() {
        UUID id = UUID.randomUUID();
        var paciente = novoPaciente();
        paciente.setAtivo(false);
        paciente.setAnonimizado(true);
        when(repository.findById(id)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> service.reativar(id))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveExportarDadosQuandoProprioPacienteTitular() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(novoPaciente()));
        when(agendamentoService.listar(id, null, null, null)).thenReturn(List.of());

        var response = service.exportar(id);

        assertThat(response.paciente().getNome()).isEqualTo("João Silva");
        assertThat(response.agendamentos()).isEmpty();
        verify(auditoriaService).registrar("PACIENTE", id, AcaoAuditoria.EXPORTACAO);
    }

    @Test
    void deveExportarDadosQuandoDesenvolvedor() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(contextoSeguranca.isDesenvolvedor()).thenReturn(true);
        when(repository.findById(id)).thenReturn(Optional.of(novoPaciente()));
        when(agendamentoService.listar(id, null, null, null)).thenReturn(List.of());

        var response = service.exportar(id);

        assertThat(response.paciente()).isNotNull();
    }

    @Test
    void deveNegarExportacaoQuandoPacienteConsultaOutroPaciente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());
        when(contextoSeguranca.isDesenvolvedor()).thenReturn(false);

        assertThatThrownBy(() -> service.exportar(id))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveAnonimizarPaciente() {
        var paciente = novoPaciente();
        UUID id = paciente.getId();
        when(repository.findById(id)).thenReturn(Optional.of(paciente));
        when(repository.save(paciente)).thenReturn(paciente);

        service.anonimizar(id);

        assertThat(paciente.getNome()).isEqualTo("ANONIMIZADO");
        assertThat(paciente.getCpfHash()).isNull();
        assertThat(paciente.getEmail()).contains("anonimizado-").contains("@sgsm.invalid");
        assertThat(paciente.getTelefone()).isNull();
        assertThat(paciente.getDataNascimento()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(paciente.getAtivo()).isFalse();
        assertThat(paciente.getAnonimizado()).isTrue();
        assertThat(paciente.getEncerradoEm()).isNotNull();
        verify(vetorizacaoPublisher).publicar("PACIENTE", id.toString(), "ANONIMIZAR");
        verify(auditoriaService).registrar("PACIENTE", id, AcaoAuditoria.ANONIMIZACAO);
    }

    @Test
    void deveLancarExcecaoAoAnonimizarPacienteJaAnonimizado() {
        UUID id = UUID.randomUUID();
        var paciente = novoPaciente();
        paciente.setAnonimizado(true);
        when(repository.findById(id)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> service.anonimizar(id))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoAnonimizarPacienteInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.anonimizar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveListarApenasProprioPacienteQuandoAutenticadoComoPaciente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(novoPaciente()));

        var resultado = service.listar(null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveRetornarListaVaziaQuandoPacienteAutenticadoNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isPaciente()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.empty());

        var resultado = service.listar(null);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveListarPacientesAtivosQuandoMedicoLogado() {
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(repository.findAllByAtivo(true)).thenReturn(List.of(novoPaciente()));

        var resultado = service.listar(true);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarTodosPacientesQuandoMedicoLogadoSemFiltroDeAtivo() {
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(novoPaciente()));

        var resultado = service.listar(null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPacientesPorAtivoQuandoNemMedicoNemPaciente() {
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(repository.findAllByAtivo(true)).thenReturn(List.of(novoPaciente()));

        var resultado = service.listar(true);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarTodosPacientesQuandoNenhumFiltroInformado() {
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(repository.findAll()).thenReturn(List.of(novoPaciente()));

        var resultado = service.listar(null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarSoPacientesDosMedicosVinculadosQuandoEscopado() {
        UUID medicoId = UUID.randomUUID();
        var paciente = novoPaciente();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(estabelecimentoAcessoService.medicosPermitidos()).thenReturn(List.of(medicoId));
        when(agendamentoRepository.findPacienteIdsDistintosByMedicoIdIn(List.of(medicoId)))
                .thenReturn(List.of(paciente.getId()));
        when(repository.findAllById(java.util.Set.of(paciente.getId()))).thenReturn(List.of(paciente));

        var resultado = service.listar(null);

        assertThat(resultado).hasSize(1);
    }

    // WhatsApp por clínica (V015): paciente que chegou pelo número da clínica ou foi cadastrado por
    // ela aparece para a equipe antes do primeiro agendamento
    @Test
    void equipeVeTambemPacientesVinculadosAClinicaSemAgendamento() {
        UUID medicoId = UUID.randomUUID();
        UUID clinica = UUID.randomUUID();
        var comAgendamento = novoPaciente();
        var vindoDoWhatsApp = novoPaciente();
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(estabelecimentoAcessoService.medicosPermitidos()).thenReturn(List.of(medicoId));
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(clinica));
        when(agendamentoRepository.findPacienteIdsDistintosByMedicoIdIn(List.of(medicoId)))
                .thenReturn(List.of(comAgendamento.getId()));
        when(pacienteClinicaService.pacientesDasClinicas(List.of(clinica)))
                .thenReturn(List.of(vindoDoWhatsApp.getId(), comAgendamento.getId()));
        when(repository.findAllById(java.util.Set.of(comAgendamento.getId(), vindoDoWhatsApp.getId())))
                .thenReturn(List.of(comAgendamento, vindoDoWhatsApp));

        assertThat(service.listar(null)).hasSize(2);
    }

    @Test
    void pacienteCadastradoPelaEquipeJaFicaVinculadoAsClinicasDela() {
        UUID clinica = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(clinica));
        when(repository.existsByCpfHash(any())).thenReturn(false);
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            var p = (Paciente) inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });

        var criado = service.cadastrar(new br.com.sgsm.dto.CadastrarPacienteRequest("Maria", "52998224725",
                java.time.LocalDate.of(1990, 1, 1), "m@a.com", null, null, null, null, null, null, null, null, true));

        verify(pacienteClinicaService).vincularAsClinicas(criado.getId(), List.of(clinica));
    }

    @Test
    void deveRetornarVazioQuandoEscopadoSemMedicoVinculado() {
        when(contextoSeguranca.isPaciente()).thenReturn(false);
        when(estabelecimentoAcessoService.medicosPermitidos()).thenReturn(List.of());

        var resultado = service.listar(null);

        assertThat(resultado).isEmpty();
    }
}
