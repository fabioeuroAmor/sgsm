package br.com.sgsm.service;

import br.com.sgsm.config.ModelMapperConfig;
import br.com.sgsm.domain.Estabelecimento;
import br.com.sgsm.domain.Medico;
import br.com.sgsm.domain.MedicoEstabelecimento;
import br.com.sgsm.dto.AssociarMedicosRequest;
import br.com.sgsm.dto.AtualizarEstabelecimentoRequest;
import br.com.sgsm.dto.CadastrarEstabelecimentoRequest;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.events.VetorizacaoPublisher;
import br.com.sgsm.repository.EstabelecimentoRepository;
import org.springframework.test.util.ReflectionTestUtils;
import br.com.sgsm.repository.MedicoEstabelecimentoRepository;
import br.com.sgsm.repository.MedicoRepository;
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
class EstabelecimentoServiceTest {

    @Mock
    private EstabelecimentoRepository repository;
    @Mock
    private MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private VetorizacaoPublisher vetorizacaoPublisher;

    private EstabelecimentoService service;

    @BeforeEach
    void setUp() {
        service = new EstabelecimentoService(repository, medicoEstabelecimentoRepository,
                medicoRepository, new ModelMapperConfig().modelMapper(), vetorizacaoPublisher);
    }

    private Estabelecimento novoEstabelecimento() {
        var e = new Estabelecimento();
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setNome("Clínica Central");
        e.setCnpj("12345678000199");
        e.setLogradouro("Rua A");
        e.setNumero("100");
        e.setBairro("Centro");
        e.setCidade("São Paulo");
        e.setUf("SP");
        e.setCep("01000-000");
        e.setAtivo(true);
        return e;
    }

    private CadastrarEstabelecimentoRequest novaRequestCadastro(String uf) {
        return new CadastrarEstabelecimentoRequest(
                "Clínica Central", "12345678000199", null, null,
                "Rua A", "100", null, "Centro", "São Paulo", uf, "01000-000");
    }

    @Test
    void deveCadastrarEstabelecimentoENormalizarUfParaMaiuscula() {
        when(repository.existsByCnpj("12345678000199")).thenReturn(false);
        when(repository.save(any(Estabelecimento.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });

        var response = service.cadastrar(novaRequestCadastro("sp"));

        assertThat(response.getUf()).isEqualTo("SP");
    }

    @Test
    void deveLancarExcecaoQuandoCnpjJaCadastrado() {
        when(repository.existsByCnpj("12345678000199")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(novaRequestCadastro("SP")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12345678000199");
        verify(repository, never()).save(any());
    }

    @Test
    void deveConsultarEstabelecimentoExistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoEstabelecimento()));

        var response = service.consultar(id);

        assertThat(response.getNome()).isEqualTo("Clínica Central");
    }

    @Test
    void deveLancarExcecaoAoConsultarEstabelecimentoInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveAtualizarEstabelecimentoENormalizarUf() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoEstabelecimento()));
        when(repository.save(any(Estabelecimento.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        var request = new AtualizarEstabelecimentoRequest(
                "Clínica Central Ltda", null, null, null, null, null, null, null, "rj", null);

        var response = service.atualizar(id, request);

        assertThat(response.getNome()).isEqualTo("Clínica Central Ltda");
        assertThat(response.getUf()).isEqualTo("RJ");
    }

    @Test
    void deveAtualizarEstabelecimentoSemAlterarUfQuandoNaoInformada() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(novoEstabelecimento()));
        when(repository.save(any(Estabelecimento.class))).thenAnswer(inv -> {
            var e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });
        var request = new AtualizarEstabelecimentoRequest(
                "Clínica Central Ltda", null, null, null, null, null, null, null, null, null);

        var response = service.atualizar(id, request);

        assertThat(response.getUf()).isEqualTo("SP");
    }

    @Test
    void deveLancarExcecaoAoAtualizarEstabelecimentoInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        var request = new AtualizarEstabelecimentoRequest(
                "x", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveInativarEstabelecimentoAoRemover() {
        UUID id = UUID.randomUUID();
        var estabelecimento = novoEstabelecimento();
        when(repository.findById(id)).thenReturn(Optional.of(estabelecimento));
        when(repository.save(estabelecimento)).thenReturn(estabelecimento);

        service.remover(id);

        assertThat(estabelecimento.getAtivo()).isFalse();
        verify(repository).save(estabelecimento);
    }

    @Test
    void deveLancarExcecaoAoRemoverEstabelecimentoInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveReativarEstabelecimento() {
        UUID id = UUID.randomUUID();
        var estabelecimento = novoEstabelecimento();
        estabelecimento.setAtivo(false);
        when(repository.findById(id)).thenReturn(Optional.of(estabelecimento));
        when(repository.save(estabelecimento)).thenReturn(estabelecimento);

        var response = service.reativar(id);

        assertThat(estabelecimento.getAtivo()).isTrue();
        assertThat(response.getAtivo()).isTrue();
        verify(repository).save(estabelecimento);
    }

    @Test
    void deveLancarExcecaoAoReativarEstabelecimentoInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reativar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveListarPorUfEAtivoQuandoAmbosInformados() {
        when(repository.findAllByUfAndAtivo("SP", true)).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(true, "sp", null);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByUfAndAtivo("SP", true);
    }

    @Test
    void deveListarPorCidadeEAtivoQuandoAmbosInformados() {
        when(repository.findAllByCidadeAndAtivo("São Paulo", true)).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(true, null, "São Paulo");

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorUfECidadeQuandoAmbosInformadosSemAtivo() {
        when(repository.findAllByUfAndCidade("GO", "Goiânia")).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(null, "go", "Goiânia");

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByUfAndCidade("GO", "Goiânia");
    }

    @Test
    void deveListarPorUfCidadeEAtivoQuandoTodosInformados() {
        when(repository.findAllByUfAndCidadeAndAtivo("GO", "Goiânia", true)).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(true, "go", "Goiânia");

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByUfAndCidadeAndAtivo("GO", "Goiânia", true);
    }

    @Test
    void deveListarPorUfQuandoApenasUfInformada() {
        when(repository.findAllByUf("SP")).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(null, "sp", null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorCidadeQuandoApenasCidadeInformada() {
        when(repository.findAllByCidade("São Paulo")).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(null, null, "São Paulo");

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarPorAtivoQuandoApenasAtivoInformado() {
        when(repository.findAllByAtivo(true)).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(true, null, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarTodosQuandoNenhumFiltroInformado() {
        when(repository.findAll()).thenReturn(List.of(novoEstabelecimento()));

        var resultado = service.listar(null, null, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarEstabelecimentosVinculadosQuandoMedicoIdInformado() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        var vinculo = new MedicoEstabelecimento(medicoId, estabelecimentoId);
        var est = novoEstabelecimento();
        when(medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(medicoId, true))
                .thenReturn(List.of(vinculo));
        when(repository.findAllById(List.of(estabelecimentoId))).thenReturn(List.of(est));

        var resultado = service.listar(null, null, null, medicoId);

        assertThat(resultado).hasSize(1);
        verify(medicoEstabelecimentoRepository).findById_MedicoIdAndAtivo(medicoId, true);
    }

    @Test
    void deveListarEstabelecimentosVinculadosAtivosQuandoMedicoIdEAtivoInformados() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        var vinculo = new MedicoEstabelecimento(medicoId, estabelecimentoId);
        var est = novoEstabelecimento();
        when(medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(medicoId, true))
                .thenReturn(List.of(vinculo));
        when(repository.findAllById(List.of(estabelecimentoId))).thenReturn(List.of(est));

        var resultado = service.listar(true, null, null, medicoId);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deveListarMedicosVinculadosAtivos() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(repository.findById(estabelecimentoId)).thenReturn(Optional.of(novoEstabelecimento()));
        var vinculo = new MedicoEstabelecimento(medicoId, estabelecimentoId);
        when(medicoEstabelecimentoRepository.findById_EstabelecimentoIdAndAtivo(estabelecimentoId, true))
                .thenReturn(List.of(vinculo));
        var medico = new Medico();
        medico.setAtivo(true);
        medico.setNome("Dr. Carlos");
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(medico));

        var resultado = service.listarMedicos(estabelecimentoId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNome()).isEqualTo("Dr. Carlos");
    }

    @Test
    void deveFiltrarMedicoInativoOuInexistenteAoListarMedicos() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID medicoInativoId = UUID.randomUUID();
        UUID medicoInexistenteId = UUID.randomUUID();
        when(repository.findById(estabelecimentoId)).thenReturn(Optional.of(novoEstabelecimento()));
        var vinculoInativo = new MedicoEstabelecimento(medicoInativoId, estabelecimentoId);
        var vinculoInexistente = new MedicoEstabelecimento(medicoInexistenteId, estabelecimentoId);
        when(medicoEstabelecimentoRepository.findById_EstabelecimentoIdAndAtivo(estabelecimentoId, true))
                .thenReturn(List.of(vinculoInativo, vinculoInexistente));
        var medicoInativo = new Medico();
        medicoInativo.setAtivo(false);
        when(medicoRepository.findById(medicoInativoId)).thenReturn(Optional.of(medicoInativo));
        when(medicoRepository.findById(medicoInexistenteId)).thenReturn(Optional.empty());

        var resultado = service.listarMedicos(estabelecimentoId);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveLancarExcecaoAoListarMedicosDeEstabelecimentoInexistente() {
        UUID estabelecimentoId = UUID.randomUUID();
        when(repository.findById(estabelecimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarMedicos(estabelecimentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveAssociarMedicosSubstituindoVinculosAnteriores() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(repository.findById(estabelecimentoId)).thenReturn(Optional.of(novoEstabelecimento()));
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(new Medico()));
        var request = new AssociarMedicosRequest(List.of(medicoId));

        service.associarMedicos(estabelecimentoId, request);

        verify(medicoEstabelecimentoRepository).deleteByEstabelecimentoId(estabelecimentoId);
        verify(medicoEstabelecimentoRepository).save(any(MedicoEstabelecimento.class));
    }

    @Test
    void deveLancarExcecaoAoAssociarMedicoInexistente() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        when(repository.findById(estabelecimentoId)).thenReturn(Optional.of(novoEstabelecimento()));
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.empty());
        var request = new AssociarMedicosRequest(List.of(medicoId));

        assertThatThrownBy(() -> service.associarMedicos(estabelecimentoId, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(medicoEstabelecimentoRepository, never()).deleteByEstabelecimentoId(any());
    }

    @Test
    void deveLancarExcecaoAoAssociarMedicosEmEstabelecimentoInexistente() {
        UUID estabelecimentoId = UUID.randomUUID();
        when(repository.findById(estabelecimentoId)).thenReturn(Optional.empty());
        var request = new AssociarMedicosRequest(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.associarMedicos(estabelecimentoId, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
