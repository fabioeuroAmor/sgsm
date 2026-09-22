package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.Funcionario;
import br.com.sgsm.dto.AtualizarFuncionarioRequest;
import br.com.sgsm.dto.CadastrarFuncionarioRequest;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.FuncionarioRepository;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuncionarioServiceTest {

    @Mock private FuncionarioRepository repository;
    @Mock private EstabelecimentoRepository estabelecimentoRepository;
    @Mock private EstabelecimentoAcessoService estabelecimentoAcessoService;

    private FuncionarioService service;

    @BeforeEach
    void setUp() {
        service = new FuncionarioService(repository, estabelecimentoRepository,
                new ModelMapperConfig().modelMapper(), estabelecimentoAcessoService);
    }

    private Funcionario novoFuncionario(UUID estabelecimentoId) {
        var f = new Funcionario();
        f.setNome("Maria Silva");
        f.setCpf("123.456.789-00");
        f.setEmail("maria@email.com");
        f.setCargo("Recepcionista");
        f.setEstabelecimentoId(estabelecimentoId);
        f.prePersist();
        return f;
    }

    // ─── cadastrar ────────────────────────────────────────────────────────────

    @Test
    void deveCadastrarFuncionarioQuandoEstabelecimentoPermitido() {
        UUID estabId = UUID.randomUUID();
        var request = new CadastrarFuncionarioRequest("Maria Silva", "123.456.789-00", "maria@email.com", null, "Recepcionista", estabId);
        when(estabelecimentoRepository.existsById(estabId)).thenReturn(true);
        when(repository.existsByCpf("123.456.789-00")).thenReturn(false);
        when(repository.save(any(Funcionario.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cadastrar(request);

        assertThat(response.getNome()).isEqualTo("Maria Silva");
        verify(estabelecimentoAcessoService).validarEstabelecimentoPermitido(estabId);
    }

    @Test
    void deveLancarExcecaoAoCadastrarFuncionarioEmEstabelecimentoNaoPermitido() {
        UUID estabId = UUID.randomUUID();
        var request = new CadastrarFuncionarioRequest("X", "000.000.000-00", "x@e.com", null, "Aux", estabId);
        when(estabelecimentoRepository.existsById(estabId)).thenReturn(true);
        doThrow(new AcessoNegadoException("Acesso negado ao estabelecimento: " + estabId))
                .when(estabelecimentoAcessoService).validarEstabelecimentoPermitido(estabId);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(AcessoNegadoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoCadastrarQuandoEstabelecimentoNaoExiste() {
        UUID estabId = UUID.randomUUID();
        var request = new CadastrarFuncionarioRequest("X", "000.000.000-00", "x@e.com", null, "Aux", estabId);
        when(estabelecimentoRepository.existsById(estabId)).thenReturn(false);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoCadastrarComCpfDuplicado() {
        UUID estabId = UUID.randomUUID();
        var request = new CadastrarFuncionarioRequest("X", "123.456.789-00", "x@e.com", null, "Aux", estabId);
        when(estabelecimentoRepository.existsById(estabId)).thenReturn(true);
        when(repository.existsByCpf("123.456.789-00")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("123.456.789-00");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoCadastrarComEmailDuplicado() {
        UUID estabId = UUID.randomUUID();
        var request = new CadastrarFuncionarioRequest("X", "123.456.789-00", "duplicado@email.com", null, "Aux", estabId);
        when(estabelecimentoRepository.existsById(estabId)).thenReturn(true);
        when(repository.existsByCpf("123.456.789-00")).thenReturn(false);
        when(repository.existsByEmail("duplicado@email.com")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado@email.com");
        verify(repository, never()).save(any());
    }

    // ─── consultar ───────────────────────────────────────────────────────────

    @Test
    void deveConsultarFuncionarioExistente() {
        UUID id = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoFuncionario(estabId)));

        var response = service.consultar(id);

        assertThat(response.getNome()).isEqualTo("Maria Silva");
        verify(estabelecimentoAcessoService).validarEstabelecimentoPermitido(estabId);
    }

    @Test
    void deveLancarExcecaoAoConsultarFuncionarioInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoAoConsultarFuncionarioDeEstabelecimentoNaoPermitido() {
        UUID estabId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoFuncionario(estabId)));
        doThrow(new AcessoNegadoException("Acesso negado ao estabelecimento: " + estabId))
                .when(estabelecimentoAcessoService).validarEstabelecimentoPermitido(estabId);

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(AcessoNegadoException.class);
    }

    // ─── listar ──────────────────────────────────────────────────────────────

    @Test
    void deveListarTodosQuandoIrrestrito() {
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(null);
        when(repository.findAll()).thenReturn(List.of(novoFuncionario(UUID.randomUUID())));

        var resultado = service.listar(null, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorEstabelecimentoQuandoIrrestrito() {
        UUID estabId = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(null);
        when(repository.findAllByEstabelecimentoId(estabId)).thenReturn(List.of(novoFuncionario(estabId)));

        var resultado = service.listar(estabId, null);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByEstabelecimentoId(estabId);
    }

    @Test
    void deveListarPorEstabelecimentoEAtivoQuandoIrrestrito() {
        UUID estabId = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(null);
        when(repository.findAllByEstabelecimentoIdAndAtivo(estabId, true)).thenReturn(List.of(novoFuncionario(estabId)));

        var resultado = service.listar(estabId, true);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByEstabelecimentoIdAndAtivo(estabId, true);
    }

    @Test
    void deveListarPorAtivoQuandoIrrestritoEApenasAtivoInformado() {
        UUID estabId = UUID.randomUUID();
        var func = novoFuncionario(estabId);
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(null);
        when(repository.findAll()).thenReturn(List.of(func));

        var resultado = service.listar(null, true);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarFuncionariosDoEstabelecimentoQuandoEscopado() {
        UUID estabId = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(estabId));
        when(repository.findAllByEstabelecimentoIdIn(List.of(estabId))).thenReturn(List.of(novoFuncionario(estabId)));

        var resultado = service.listar(null, null);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByEstabelecimentoIdIn(List.of(estabId));
    }

    @Test
    void deveListarPorEstabelecimentoEspecificoQuandoEscopado() {
        UUID estabId = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(estabId));
        when(repository.findAllByEstabelecimentoId(estabId)).thenReturn(List.of(novoFuncionario(estabId)));

        var resultado = service.listar(estabId, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveLancarExcecaoAoListarEstabelecimentoNaoPermitido() {
        UUID estabId = UUID.randomUUID();
        UUID outroEstabId = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(outroEstabId));

        assertThatThrownBy(() -> service.listar(estabId, null))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void deveListarComAtivoEEstabelecimentoQuandoEscopado() {
        UUID estabId = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(estabId));
        when(repository.findAllByEstabelecimentoIdAndAtivo(estabId, true)).thenReturn(List.of(novoFuncionario(estabId)));

        var resultado = service.listar(estabId, true);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByEstabelecimentoIdAndAtivo(estabId, true);
    }

    @Test
    void deveListarComAtivoSemEstabelecimentoQuandoEscopado() {
        UUID estabId = UUID.randomUUID();
        when(estabelecimentoAcessoService.estabelecimentosPermitidos()).thenReturn(List.of(estabId));
        when(repository.findAllByEstabelecimentoIdInAndAtivo(List.of(estabId), true)).thenReturn(List.of(novoFuncionario(estabId)));

        var resultado = service.listar(null, true);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByEstabelecimentoIdInAndAtivo(List.of(estabId), true);
    }

    // ─── atualizar ───────────────────────────────────────────────────────────

    @Test
    void deveAtualizarFuncionario() {
        UUID id = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoFuncionario(estabId)));
        when(repository.existsByEmailAndIdNot(any(), eq(id))).thenReturn(false);
        when(repository.save(any(Funcionario.class))).thenAnswer(inv -> inv.getArgument(0));
        var request = new AtualizarFuncionarioRequest("Maria Souza", "novo@email.com", null, null);

        var response = service.atualizar(id, request);

        assertThat(response.getNome()).isEqualTo("Maria Souza");
    }

    @Test
    void deveLancarExcecaoAoAtualizarComEmailDuplicado() {
        UUID id = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoFuncionario(estabId)));
        when(repository.existsByEmailAndIdNot("dup@email.com", id)).thenReturn(true);
        var request = new AtualizarFuncionarioRequest(null, "dup@email.com", null, null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoAtualizarFuncionarioInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(id, new AtualizarFuncionarioRequest("x", null, null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ─── remover ─────────────────────────────────────────────────────────────

    @Test
    void deveInativarFuncionarioAoRemover() {
        UUID id = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        var func = novoFuncionario(estabId);
        when(repository.findById(id)).thenReturn(Optional.of(func));

        service.remover(id);

        assertThat(func.getAtivo()).isFalse();
        verify(repository).save(func);
    }

    @Test
    void deveLancarExcecaoAoRemoverFuncionarioInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoAoRemoverFuncionarioDeEstabelecimentoNaoPermitido() {
        UUID estabId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoFuncionario(estabId)));
        doThrow(new AcessoNegadoException("Acesso negado ao estabelecimento: " + estabId))
                .when(estabelecimentoAcessoService).validarEstabelecimentoPermitido(estabId);

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(AcessoNegadoException.class);
        verify(repository, never()).save(any());
    }

    // ─── reativar ────────────────────────────────────────────────────────────

    @Test
    void deveReativarFuncionarioQuandoPermitido() {
        UUID id = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        var func = novoFuncionario(estabId);
        func.setAtivo(false);
        when(repository.findById(id)).thenReturn(Optional.of(func));
        when(repository.save(func)).thenReturn(func);

        var response = service.reativar(id);

        assertThat(func.getAtivo()).isTrue();
        assertThat(response.getAtivo()).isTrue();
        verify(repository).save(func);
    }

    @Test
    void deveLancarExcecaoAoReativarFuncionarioDeEstabelecimentoNaoPermitido() {
        UUID estabId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoFuncionario(estabId)));
        doThrow(new AcessoNegadoException("Acesso negado ao estabelecimento: " + estabId))
                .when(estabelecimentoAcessoService).validarEstabelecimentoPermitido(estabId);

        assertThatThrownBy(() -> service.reativar(id))
                .isInstanceOf(AcessoNegadoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoReativarFuncionarioInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reativar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
