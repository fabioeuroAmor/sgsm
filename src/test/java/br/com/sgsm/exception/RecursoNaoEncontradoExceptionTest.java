package br.com.sgsm.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecursoNaoEncontradoExceptionTest {

    @Test
    void devePreservarMensagemDaExcecao() {
        var ex = new RecursoNaoEncontradoException("Médico não encontrado: 123");

        assertThat(ex.getMessage()).isEqualTo("Médico não encontrado: 123");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
