package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.AcaoAuditoria;
import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.Paciente;
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

    private CpfCryptoService cpfCryptoService;
    private PacienteService service;

    @BeforeEach
    void setUp() {
        String chaveAes = Base64.getEncoder().encodeToString("chave-teste-de-32-bytes-exatos!!".getBytes());
        String chaveHmac = Base64.getEncoder().encodeToString("outra-chave-de-32-bytes-teste!!!".getBytes());
        cpfCryptoService = new CpfCryptoService(chaveAes, chaveHmac);
        service = new PacienteService(repository, agendamentoRepository,
                new ModelMapperConfig().modelMapper(), contextoSeguranca, vetorizacaoPublisher, auditoriaService,
                cpfCryptoService);
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
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null);

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
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF inválido");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoCpfJaCadastrado() {
        when(repository.existsByCpfHash(cpfCryptoService.hash("52998224725"))).thenReturn(true);
        var request = new CadastrarPacienteRequest("João Silva", "52998224725",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("52998224725");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoEmailJaCadastrado() {
        when(repository.existsByEmail("joao@sgsm.com.br")).thenReturn(true);
        var request = new CadastrarPacienteRequest("João Silva", "52998224725",
                LocalDate.of(1990, 1, 1), "joao@sgsm.com.br", null, null, null, null, null, null, null, null);

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
        when(repository.findById(id)).thenReturn(Optional.of(paciente));
        when(repository.save(paciente)).thenReturn(paciente);

        var response = service.reativar(id);

        assertThat(paciente.getAtivo()).isTrue();
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
}
