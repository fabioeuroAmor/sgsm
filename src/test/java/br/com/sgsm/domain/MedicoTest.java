package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a regra de ativação automática do médico ao ser persistido.
 */
class MedicoTest {

    @Test
    void prePersistDeveAtivarMedicoEDefinirTimestamps() {
        var medico = new Medico();

        medico.prePersist();

        assertThat(medico.getAtivo()).isTrue();
        assertThat(medico.getCriadoEm()).isNotNull();
        assertThat(medico.getAtualizadoEm()).isNotNull();
    }

    @Test
    void preUpdateDeveAtualizarDataDeAtualizacao() {
        var medico = new Medico();
        medico.prePersist();

        medico.preUpdate();

        assertThat(medico.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarTelefoneInformado() {
        var medico = new Medico();

        medico.setTelefone("11988887777");

        assertThat(medico.getTelefone()).isEqualTo("11988887777");
    }
}
