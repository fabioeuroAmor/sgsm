package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FuncionarioTest {

    @Test
    void prePersistDeveAtivarFuncionarioEDefinirTimestamps() {
        var func = new Funcionario();

        func.prePersist();

        assertThat(func.getAtivo()).isTrue();
        assertThat(func.getCriadoEm()).isNotNull();
        assertThat(func.getAtualizadoEm()).isNotNull();
    }

    @Test
    void preUpdateDeveAtualizarDataDeAtualizacao() {
        var func = new Funcionario();
        func.prePersist();

        func.preUpdate();

        assertThat(func.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarTelefoneInformado() {
        var func = new Funcionario();

        func.setTelefone("11988887777");

        assertThat(func.getTelefone()).isEqualTo("11988887777");
    }

    @Test
    void deveArmazenarCargoInformado() {
        var func = new Funcionario();

        func.setCargo("Recepcionista");

        assertThat(func.getCargo()).isEqualTo("Recepcionista");
    }
}
