package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.Medico;
import br.com.sgsm.dto.AtualizarMedicoRequest;
import br.com.sgsm.dto.CadastrarMedicoRequest;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.repository.MedicoRepository;
import org.springframework.test.util.ReflectionTestUtils;
import br.com.sgsm.security.ContextoSeguranca;
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
class MedicoServiceTest {

    @Mock
    private MedicoRepository repository;
    @Mock
    private ContextoSeguranca contextoSeguranca;
    @Mock
    private VetorizacaoPublisher vetorizacaoPublisher;

    private MedicoService service;

    @BeforeEach
    void setUp() {
        service = new MedicoService(repository, new ModelMapperConfig().modelMapper(), contextoSeguranca, vetorizacaoPublisher);
    }

    private Medico novoMedico() {
        var m = new Medico();
        m.setNome("Dra. Ana Souza");
        m.setCrm("12345");
        m.setCrmUf("sp");
        m.setEspecialidade("Cardiologia");
        m.setEmail("ana@sgsm.com.br");
        m.setAtivo(true);
        return m;
    }

    @Test
    void deveCadastrarMedicoQuandoCrmEEmailInexistentes() {
        when(repository.existsByCrmAndCrmUf("12345", "SP")).thenReturn(false);
        when(repository.existsByEmail("ana@sgsm.com.br")).thenReturn(false);
        when(repository.save(any(Medico.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        var request = new CadastrarMedicoRequest("Dra. Ana Souza", "12345", "SP", "Cardiologia", "ana@sgsm.com.br", null);

        var response = service.cadastrar(request);

        assertThat(response.getCrmUf()).isEqualTo("SP");
        assertThat(response.getNome()).isEqualTo("Dra. Ana Souza");
    }

    @Test
    void deveNormalizarUfDoCrmParaMaiuscula() {
        when(repository.save(any(Medico.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        var request = new CadastrarMedicoRequest("Dra. Ana Souza", "12345", "sp", "Cardiologia", "ana@sgsm.com.br", null);

        var response = service.cadastrar(request);

        assertThat(response.getCrmUf()).isEqualTo("SP");
    }

    @Test
    void deveLancarExcecaoQuandoCrmJaCadastrado() {
        when(repository.existsByCrmAndCrmUf("12345", "SP")).thenReturn(true);
        var request = new CadastrarMedicoRequest("Dra. Ana Souza", "12345", "SP", "Cardiologia", "ana@sgsm.com.br", null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12345");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoEmailJaCadastrado() {
        when(repository.existsByCrmAndCrmUf("12345", "SP")).thenReturn(false);
        when(repository.existsByEmail("ana@sgsm.com.br")).thenReturn(true);
        var request = new CadastrarMedicoRequest("Dra. Ana Souza", "12345", "SP", "Cardiologia", "ana@sgsm.com.br", null);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ana@sgsm.com.br");
        verify(repository, never()).save(any());
    }

    @Test
    void deveConsultarMedicoQuandoNaoForMedicoAutenticado() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(novoMedico()));

        var response = service.consultar(id);

        assertThat(response.getNome()).isEqualTo("Dra. Ana Souza");
    }

    @Test
    void deveConsultarProprioMedicoQuandoAutenticadoComoMedico() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(novoMedico()));

        var response = service.consultar(id);

        assertThat(response).isNotNull();
    }

    @Test
    void deveNegarAcessoQuandoMedicoConsultaOutroMedico() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveLancarExcecaoAoConsultarMedicoInexistente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveAtualizarMedicoQuandoDadosValidos() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(novoMedico()));
        when(repository.save(any(Medico.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        var request = new AtualizarMedicoRequest("Dra. Ana Souza Lima", null, null, null);

        var response = service.atualizar(id, request);

        assertThat(response.getNome()).isEqualTo("Dra. Ana Souza Lima");
    }

    @Test
    void deveNegarAtualizacaoQuandoMedicoEditaOutroMedico() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());
        var request = new AtualizarMedicoRequest("x", null, null, null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveLancarExcecaoAoAtualizarComEmailJaCadastradoPorOutroMedico() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(novoMedico()));
        when(repository.existsByEmailAndIdNot("novo@sgsm.com.br", id)).thenReturn(true);
        var request = new AtualizarMedicoRequest(null, null, "novo@sgsm.com.br", null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveInativarMedicoAoRemover() {
        UUID id = UUID.randomUUID();
        var medico = novoMedico();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(medico));

        service.remover(id);

        assertThat(medico.getAtivo()).isFalse();
        verify(repository).save(medico);
    }

    @Test
    void devePermitirMedicoRemoverASiMesmo() {
        UUID id = UUID.randomUUID();
        var medico = novoMedico();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(medico));

        service.remover(id);

        assertThat(medico.getAtivo()).isFalse();
        verify(repository).save(medico);
    }

    @Test
    void deveNegarRemocaoQuandoMedicoRemoveOutroMedico() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveReativarMedicoQuandoNaoForMedicoAutenticado() {
        UUID id = UUID.randomUUID();
        var medico = novoMedico();
        medico.setAtivo(false);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(medico));
        when(repository.save(medico)).thenReturn(medico);

        var response = service.reativar(id);

        assertThat(medico.getAtivo()).isTrue();
        assertThat(response.getAtivo()).isTrue();
        verify(repository).save(medico);
    }

    @Test
    void devePermitirMedicoReativarASiMesmo() {
        UUID id = UUID.randomUUID();
        var medico = novoMedico();
        medico.setAtivo(false);
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(medico));
        when(repository.save(medico)).thenReturn(medico);

        var response = service.reativar(id);

        assertThat(medico.getAtivo()).isTrue();
        assertThat(response.getAtivo()).isTrue();
        verify(repository).save(medico);
    }

    @Test
    void deveNegarReativacaoQuandoMedicoReativaOutroMedico() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.reativar(id))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void deveLancarExcecaoAoReativarMedicoInexistente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reativar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveLancarExcecaoAoRemoverMedicoInexistente() {
        UUID id = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveListarTodosOsMedicosMesmoQuandoAutenticadoComoMedico() {
        when(repository.findAll()).thenReturn(List.of(novoMedico(), novoMedico()));

        var resultado = service.listar(null, null);

        assertThat(resultado).hasSize(2);
        verify(contextoSeguranca, never()).isMedico();
    }

    @Test
    void deveListarPorEspecialidadeEAtivoQuandoAmbosInformados() {
        when(repository.findAllByEspecialidadeAndAtivo("Cardiologia", true)).thenReturn(List.of(novoMedico()));

        var resultado = service.listar(true, "Cardiologia");

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorEspecialidadeQuandoApenasEspecialidadeInformada() {
        when(repository.findAllByEspecialidade("Cardiologia")).thenReturn(List.of(novoMedico()));

        var resultado = service.listar(null, "Cardiologia");

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorAtivoQuandoApenasAtivoInformado() {
        when(repository.findAllByAtivo(true)).thenReturn(List.of(novoMedico()));

        var resultado = service.listar(true, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarTodosQuandoNenhumFiltroInformado() {
        when(repository.findAll()).thenReturn(List.of(novoMedico()));

        var resultado = service.listar(null, null);

        assertThat(resultado).hasSize(1);
    }
}
