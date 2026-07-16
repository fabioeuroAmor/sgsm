package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a regra de ativação automática do vínculo médico-estabelecimento ao ser persistido.
 */
class MedicoEstabelecimentoTest {

    @Test
    void construtorDeveMontarIdComposto() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();

        var vinculo = new MedicoEstabelecimento(medicoId, estabelecimentoId);

        assertThat(vinculo.getId().getMedicoId()).isEqualTo(medicoId);
        assertThat(vinculo.getId().getEstabelecimentoId()).isEqualTo(estabelecimentoId);
    }

    @Test
    void prePersistDeveAtivarVinculoEDefinirDataDeCriacao() {
        var vinculo = new MedicoEstabelecimento(UUID.randomUUID(), UUID.randomUUID());

        vinculo.prePersist();

        assertThat(vinculo.getAtivo()).isTrue();
        assertThat(vinculo.getCriadoEm()).isNotNull();
    }

    @Test
    void deveSobrescreverAtivoManualmente() {
        var vinculo = new MedicoEstabelecimento(UUID.randomUUID(), UUID.randomUUID());

        vinculo.setAtivo(false);

        assertThat(vinculo.getAtivo()).isFalse();
    }
}
