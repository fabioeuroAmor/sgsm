package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a regra de ativação automática do estabelecimento ao ser persistido.
 */
class EstabelecimentoTest {

    @Test
    void prePersistDeveAtivarEstabelecimentoEDefinirTimestamps() {
        var estabelecimento = new Estabelecimento();

        estabelecimento.prePersist();

        assertThat(estabelecimento.getAtivo()).isTrue();
        assertThat(estabelecimento.getCriadoEm()).isNotNull();
        assertThat(estabelecimento.getAtualizadoEm()).isNotNull();
    }

    @Test
    void preUpdateDeveAtualizarDataDeAtualizacao() {
        var estabelecimento = new Estabelecimento();
        estabelecimento.prePersist();

        estabelecimento.preUpdate();

        assertThat(estabelecimento.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarDadosDeContatoEComplemento() {
        var estabelecimento = new Estabelecimento();

        estabelecimento.setTelefone("11999999999");
        estabelecimento.setEmail("contato@clinica.com.br");
        estabelecimento.setComplemento("Sala 10");

        assertThat(estabelecimento.getTelefone()).isEqualTo("11999999999");
        assertThat(estabelecimento.getEmail()).isEqualTo("contato@clinica.com.br");
        assertThat(estabelecimento.getComplemento()).isEqualTo("Sala 10");
    }
}
