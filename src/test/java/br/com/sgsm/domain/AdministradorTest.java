package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a regra de ativação automática do administrador ao ser persistido.
 */
class AdministradorTest {

    @Test
    void prePersistDeveAtivarAdministradorEDefinirTimestamps() {
        var administrador = new Administrador();

        administrador.prePersist();

        assertThat(administrador.getAtivo()).isTrue();
        assertThat(administrador.getCriadoEm()).isNotNull();
        assertThat(administrador.getAtualizadoEm()).isNotNull();
    }

    @Test
    void preUpdateDeveAtualizarDataDeAtualizacao() {
        var administrador = new Administrador();
        administrador.prePersist();

        administrador.preUpdate();

        assertThat(administrador.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarTelefoneInformado() {
        var administrador = new Administrador();

        administrador.setTelefone("11988887777");

        assertThat(administrador.getTelefone()).isEqualTo("11988887777");
    }
}
