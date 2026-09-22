package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.Administrador;
import br.com.sgsm.domain.AdministradorEstabelecimento;
import br.com.sgsm.domain.Estabelecimento;
import br.com.sgsm.dto.AssociarEstabelecimentosRequest;
import br.com.sgsm.dto.AtualizarAdministradorRequest;
import br.com.sgsm.dto.CadastrarAdministradorRequest;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.AdministradorEstabelecimentoRepository;
import br.com.sgsm.repository.AdministradorRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceTest {

    @Mock private AdministradorRepository repository;
    @Mock private AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository;
    @Mock private EstabelecimentoRepository estabelecimentoRepository;

    private AdministradorService service;

    @BeforeEach
    void setUp() {
        service = new AdministradorService(repository, administradorEstabelecimentoRepository,
                estabelecimentoRepository, new ModelMapperConfig().modelMapper());
    }

    private Administrador novoAdministrador() {
        var a = new Administrador();
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        a.setNome("Guilherme Dono");
        a.setCpf("123.456.789-00");
        a.setEmail("guilherme@email.com");
        a.prePersist();
        return a;
    }

    private Estabelecimento novoEstabelecimento() {
        var e = new Estabelecimento();
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setNome("Clínica São Lucas");
        e.setAtivo(true);
        return e;
    }

    // ─── cadastrar ────────────────────────────────────────────────────────────

    @Test
    void deveCadastrarAdministrador() {
        var request = new CadastrarAdministradorRequest("Guilherme Dono", "123.456.789-00", "guilherme@email.com", null);
        when(repository.existsByCpf("123.456.789-00")).thenReturn(false);
        when(repository.existsByEmail("guilherme@email.com")).thenReturn(false);
        when(repository.save(any(Administrador.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cadastrar(request);

        assertThat(response.getNome()).isEqualTo("Guilherme Dono");
    }

    @Test
    void deveLancarExcecaoAoCadastrarComCpfDuplicado() {
        var request = new CadastrarAdministradorRequest("X", "123.456.789-00", "x@e.com", null);
        when(repository.existsByCpf("123.456.789-00")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("123.456.789-00");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoCadastrarComEmailDuplicado() {
        var request = new CadastrarAdministradorRequest("X", "123.456.789-00", "duplicado@email.com", null);
        when(repository.existsByCpf("123.456.789-00")).thenReturn(false);
        when(repository.existsByEmail("duplicado@email.com")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado@email.com");
        verify(repository, never()).save(any());
    }

    // ─── consultar / listar ──────────────────────────────────────────────────

    @Test
    void deveConsultarAdministradorExistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoAdministrador()));

        var response = service.consultar(id);

        assertThat(response.getNome()).isEqualTo("Guilherme Dono");
    }

    @Test
    void deveLancarExcecaoAoConsultarAdministradorInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveListarTodosOsAdministradores() {
        when(repository.findAll()).thenReturn(List.of(novoAdministrador()));

        var resultado = service.listar(null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarAdministradoresPorAtivo() {
        when(repository.findAllByAtivo(true)).thenReturn(List.of(novoAdministrador()));

        var resultado = service.listar(true);

        assertThat(resultado).hasSize(1);
    }

    // ─── atualizar ───────────────────────────────────────────────────────────

    @Test
    void deveAtualizarAdministrador() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoAdministrador()));
        when(repository.existsByEmailAndIdNot(any(), eq(id))).thenReturn(false);
        when(repository.save(any(Administrador.class))).thenAnswer(inv -> inv.getArgument(0));
        var request = new AtualizarAdministradorRequest("Guilherme Souza", "novo@email.com", null);

        var response = service.atualizar(id, request);

        assertThat(response.getNome()).isEqualTo("Guilherme Souza");
    }

    @Test
    void deveLancarExcecaoAoAtualizarComEmailDuplicado() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoAdministrador()));
        when(repository.existsByEmailAndIdNot("dup@email.com", id)).thenReturn(true);
        var request = new AtualizarAdministradorRequest(null, "dup@email.com", null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    // ─── remover / reativar ──────────────────────────────────────────────────

    @Test
    void deveInativarAdministradorAoRemover() {
        UUID id = UUID.randomUUID();
        var admin = novoAdministrador();
        when(repository.findById(id)).thenReturn(Optional.of(admin));

        service.remover(id);

        assertThat(admin.getAtivo()).isFalse();
        verify(repository).save(admin);
    }

    @Test
    void deveReativarAdministrador() {
        UUID id = UUID.randomUUID();
        var admin = novoAdministrador();
        admin.setAtivo(false);
        when(repository.findById(id)).thenReturn(Optional.of(admin));
        when(repository.save(admin)).thenReturn(admin);

        var response = service.reativar(id);

        assertThat(admin.getAtivo()).isTrue();
        assertThat(response.getAtivo()).isTrue();
    }

    // ─── vínculo com estabelecimentos ────────────────────────────────────────

    @Test
    void deveListarEstabelecimentosAdministrados() {
        UUID administradorId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(repository.findById(administradorId)).thenReturn(Optional.of(novoAdministrador()));
        var vinculo = new AdministradorEstabelecimento(administradorId, estabelecimentoId);
        when(administradorEstabelecimentoRepository.findById_AdministradorIdAndAtivo(administradorId, true))
                .thenReturn(List.of(vinculo));
        var estabelecimento = novoEstabelecimento();
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(estabelecimento));

        var resultado = service.listarEstabelecimentos(administradorId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNome()).isEqualTo("Clínica São Lucas");
    }

    @Test
    void deveVincularEstabelecimentosSubstituindoVinculosAnteriores() {
        UUID administradorId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(repository.findById(administradorId)).thenReturn(Optional.of(novoAdministrador()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(novoEstabelecimento()));
        var request = new AssociarEstabelecimentosRequest(List.of(estabelecimentoId));

        service.vincularEstabelecimentos(administradorId, request);

        verify(administradorEstabelecimentoRepository).deleteByAdministradorId(administradorId);
        verify(administradorEstabelecimentoRepository).save(any(AdministradorEstabelecimento.class));
    }

    @Test
    void deveLancarExcecaoAoVincularEstabelecimentoInexistente() {
        UUID administradorId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        when(repository.findById(administradorId)).thenReturn(Optional.of(novoAdministrador()));
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.empty());
        var request = new AssociarEstabelecimentosRequest(List.of(estabelecimentoId));

        assertThatThrownBy(() -> service.vincularEstabelecimentos(administradorId, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(administradorEstabelecimentoRepository, never()).deleteByAdministradorId(any());
    }
}
