package br.com.sgsm.security;

import br.com.sgsm.domain.AdministradorEstabelecimento;
import br.com.sgsm.domain.Funcionario;
import br.com.sgsm.domain.MedicoEstabelecimento;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.repository.AdministradorEstabelecimentoRepository;
import br.com.sgsm.repository.FuncionarioRepository;
import br.com.sgsm.repository.MedicoEstabelecimentoRepository;
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
import static org.mockito.Mockito.*;

/**
 * Componente central de escopo por estabelecimento, compartilhado por todos os services que
 * restringem MEDICO/FUNCIONARIO/ADMIN_ESTABELECIMENTO ao(s) próprio(s) estabelecimento(s).
 * Cobre os 4 papéis (DESENVOLVEDOR irrestrito, MEDICO, FUNCIONARIO, ADMIN_ESTABELECIMENTO) nos
 * métodos públicos.
 */
@ExtendWith(MockitoExtension.class)
class EstabelecimentoAcessoServiceTest {

    @Mock private ContextoSeguranca contextoSeguranca;
    @Mock private MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    @Mock private AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository;
    @Mock private FuncionarioRepository funcionarioRepository;

    private EstabelecimentoAcessoService service;

    @BeforeEach
    void setUp() {
        service = new EstabelecimentoAcessoService(contextoSeguranca, medicoEstabelecimentoRepository,
                administradorEstabelecimentoRepository, funcionarioRepository);
    }

    // ─── estabelecimentosPermitidos ──────────────────────────────────────────

    @Test
    void deveRetornarNuloQuandoDesenvolvedorIrrestrito() {
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(false);
        when(contextoSeguranca.isAdminEstabelecimento()).thenReturn(false);

        assertThat(service.estabelecimentosPermitidos()).isNull();
    }

    @Test
    void deveRetornarEstabelecimentosDoMedico() {
        UUID medicoId = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(medicoId);
        when(medicoEstabelecimentoRepository.findById_MedicoIdAndAtivo(medicoId, true))
                .thenReturn(List.of(new MedicoEstabelecimento(medicoId, estabId)));

        assertThat(service.estabelecimentosPermitidos()).containsExactly(estabId);
    }

    @Test
    void deveRetornarEstabelecimentoUnicoDoFuncionario() {
        UUID funcionarioId = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        var funcionario = new Funcionario();
        funcionario.setEstabelecimentoId(estabId);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.of(funcionario));

        assertThat(service.estabelecimentosPermitidos()).containsExactly(estabId);
    }

    @Test
    void deveRetornarVazioQuandoFuncionarioNaoEncontrado() {
        UUID funcionarioId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.empty());

        assertThat(service.estabelecimentosPermitidos()).isEmpty();
    }

    @Test
    void deveRetornarEstabelecimentosDoAdministrador() {
        UUID administradorId = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(false);
        when(contextoSeguranca.isAdminEstabelecimento()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(administradorId);
        when(administradorEstabelecimentoRepository.findById_AdministradorIdAndAtivo(administradorId, true))
                .thenReturn(List.of(new AdministradorEstabelecimento(administradorId, estabId)));

        assertThat(service.estabelecimentosPermitidos()).containsExactly(estabId);
    }

    // ─── validarEstabelecimentoPermitido ─────────────────────────────────────

    @Test
    void naoDeveLancarExcecaoQuandoIrrestrito() {
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(false);
        when(contextoSeguranca.isAdminEstabelecimento()).thenReturn(false);

        service.validarEstabelecimentoPermitido(UUID.randomUUID());
        // não lança - comportamento esperado para papel irrestrito (ex.: DESENVOLVEDOR)
    }

    @Test
    void naoDeveLancarExcecaoQuandoEstabelecimentoPermitido() {
        UUID funcionarioId = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        var funcionario = new Funcionario();
        funcionario.setEstabelecimentoId(estabId);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.of(funcionario));

        service.validarEstabelecimentoPermitido(estabId);
    }

    @Test
    void deveLancarExcecaoQuandoEstabelecimentoNaoPermitido() {
        UUID funcionarioId = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        UUID outroEstabId = UUID.randomUUID();
        var funcionario = new Funcionario();
        funcionario.setEstabelecimentoId(estabId);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.of(funcionario));

        assertThatThrownBy(() -> service.validarEstabelecimentoPermitido(outroEstabId))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void deveLancarExcecaoQuandoEstabelecimentoNuloEEscopado() {
        UUID funcionarioId = UUID.randomUUID();
        var funcionario = new Funcionario();
        funcionario.setEstabelecimentoId(UUID.randomUUID());
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.of(funcionario));

        assertThatThrownBy(() -> service.validarEstabelecimentoPermitido(null))
                .isInstanceOf(AcessoNegadoException.class);
    }

    // ─── medicoPermitido ──────────────────────────────────────────────────────

    @Test
    void medicoPermitidoDeveSerTrueQuandoIrrestrito() {
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(false);
        when(contextoSeguranca.isAdminEstabelecimento()).thenReturn(false);

        assertThat(service.medicoPermitido(UUID.randomUUID())).isTrue();
    }

    @Test
    void medicoPermitidoDeveSerFalseQuandoEscopadoSemEstabelecimentoPermitido() {
        UUID funcionarioId = UUID.randomUUID();
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.empty());

        assertThat(service.medicoPermitido(UUID.randomUUID())).isFalse();
    }

    @Test
    void medicoPermitidoDeveConsultarVinculoAtivoQuandoEscopado() {
        UUID funcionarioId = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        var funcionario = new Funcionario();
        funcionario.setEstabelecimentoId(estabId);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.of(funcionario));
        when(medicoEstabelecimentoRepository.existsVinculoAtivo(medicoId, List.of(estabId))).thenReturn(true);

        assertThat(service.medicoPermitido(medicoId)).isTrue();
    }

    // ─── medicosPermitidos ────────────────────────────────────────────────────

    @Test
    void medicosPermitidosDeveSerNuloQuandoIrrestrito() {
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(false);
        when(contextoSeguranca.isAdminEstabelecimento()).thenReturn(false);

        assertThat(service.medicosPermitidos()).isNull();
    }

    @Test
    void medicosPermitidosDeveResolverMedicosDosEstabelecimentosPermitidos() {
        UUID funcionarioId = UUID.randomUUID();
        UUID estabId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        var funcionario = new Funcionario();
        funcionario.setEstabelecimentoId(estabId);
        when(contextoSeguranca.isMedico()).thenReturn(false);
        when(contextoSeguranca.isFuncionario()).thenReturn(true);
        when(contextoSeguranca.getReferenciaId()).thenReturn(funcionarioId);
        when(funcionarioRepository.findById(funcionarioId)).thenReturn(Optional.of(funcionario));
        when(medicoEstabelecimentoRepository.findById_EstabelecimentoIdInAndAtivo(List.of(estabId), true))
                .thenReturn(List.of(new MedicoEstabelecimento(medicoId, estabId)));

        assertThat(service.medicosPermitidos()).containsExactly(medicoId);
    }
}
