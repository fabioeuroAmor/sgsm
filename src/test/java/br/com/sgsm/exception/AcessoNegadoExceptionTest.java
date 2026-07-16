package br.com.sgsm.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcessoNegadoExceptionTest {

    @Test
    void devePreservarMensagemDaExcecao() {
        var ex = new AcessoNegadoException("Acesso negado ao paciente: 123");

        assertThat(ex.getMessage()).isEqualTo("Acesso negado ao paciente: 123");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
