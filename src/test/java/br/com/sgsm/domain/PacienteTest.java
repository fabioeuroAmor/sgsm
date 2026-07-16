package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a regra de ativação automática do paciente ao ser persistido.
 */
class PacienteTest {

    @Test
    void prePersistDeveAtivarPacienteEDefinirTimestamps() {
        var paciente = new Paciente();

        paciente.prePersist();

        assertThat(paciente.getAtivo()).isTrue();
        assertThat(paciente.getCriadoEm()).isNotNull();
        assertThat(paciente.getAtualizadoEm()).isNotNull();
    }

    @Test
    void preUpdateDeveAtualizarDataDeAtualizacao() {
        var paciente = new Paciente();
        paciente.prePersist();

        paciente.preUpdate();

        assertThat(paciente.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarTelefoneEComplementoInformados() {
        var paciente = new Paciente();

        paciente.setTelefone("11988887777");
        paciente.setComplemento("Apto 101");

        assertThat(paciente.getTelefone()).isEqualTo("11988887777");
        assertThat(paciente.getComplemento()).isEqualTo("Apto 101");
    }
}
