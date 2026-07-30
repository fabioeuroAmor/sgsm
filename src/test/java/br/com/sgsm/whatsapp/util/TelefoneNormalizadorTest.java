package br.com.sgsm.whatsapp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelefoneNormalizadorTest {

    @Test
    void apenasDigitosRemoveTudoQueNaoEDigito() {
        assertThat(TelefoneNormalizador.apenasDigitos("+55 (61) 99999-8888")).isEqualTo("5561999998888");
    }

    @Test
    void apenasDigitosRetornaVazioParaNulo() {
        assertThat(TelefoneNormalizador.apenasDigitos(null)).isEmpty();
    }

    @Test
    void variantesGeraComESemDdiQuandoNumeroJaTemDdi() {
        assertThat(TelefoneNormalizador.variantes("5561999998888"))
                .containsExactly("5561999998888", "61999998888");
    }

    @Test
    void variantesGeraComESemDdiQuandoNumeroNaoTemDdi() {
        assertThat(TelefoneNormalizador.variantes("61999998888"))
                .containsExactly("61999998888", "5561999998888");
    }

    @Test
    void variantesRetornaVazioQuandoTelefoneVazio() {
        assertThat(TelefoneNormalizador.variantes("")).isEmpty();
        assertThat(TelefoneNormalizador.variantes(null)).isEmpty();
    }
}
