package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a regra de ativação automática do vínculo administrador-estabelecimento ao ser
 * persistido.
 */
class AdministradorEstabelecimentoTest {

    @Test
    void construtorDeveMontarIdComposto() {
        UUID administradorId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();

        var vinculo = new AdministradorEstabelecimento(administradorId, estabelecimentoId);

        assertThat(vinculo.getId().getAdministradorId()).isEqualTo(administradorId);
        assertThat(vinculo.getId().getEstabelecimentoId()).isEqualTo(estabelecimentoId);
    }

    @Test
    void prePersistDeveAtivarVinculoEDefinirDataDeCriacao() {
        var vinculo = new AdministradorEstabelecimento(UUID.randomUUID(), UUID.randomUUID());

        vinculo.prePersist();

        assertThat(vinculo.getAtivo()).isTrue();
        assertThat(vinculo.getCriadoEm()).isNotNull();
    }

    @Test
    void deveSobrescreverAtivoManualmente() {
        var vinculo = new AdministradorEstabelecimento(UUID.randomUUID(), UUID.randomUUID());

        vinculo.setAtivo(false);

        assertThat(vinculo.getAtivo()).isFalse();
    }
}
