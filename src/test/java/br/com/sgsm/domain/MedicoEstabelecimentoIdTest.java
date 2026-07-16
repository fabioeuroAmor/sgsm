package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MedicoEstabelecimentoId é a chave composta (@EmbeddedId) de MedicoEstabelecimento. Seu
 * contrato de equals/hashCode é usado pelo Hibernate/JPA para identidade de entidade — por
 * isso merece verificação explícita (comportamento real, não apenas getters triviais).
 */
class MedicoEstabelecimentoIdTest {

    @Test
    void doisIdsComMesmosValoresDevemSerIguais() {
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();

        var id1 = new MedicoEstabelecimentoId(medicoId, estabelecimentoId);
        var id2 = new MedicoEstabelecimentoId(medicoId, estabelecimentoId);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void deveSerIgualASiMesmo() {
        var id = new MedicoEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(id).isEqualTo(id);
    }

    @Test
    void idsComValoresDiferentesNaoDevemSerIguais() {
        var id1 = new MedicoEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());
        var id2 = new MedicoEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void naoDeveSerIgualAObjetoDeOutroTipo() {
        var id = new MedicoEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(id).isNotEqualTo("não é um MedicoEstabelecimentoId");
    }

    @Test
    void construtorPadraoDeveCriarIdVazio() {
        var id = new MedicoEstabelecimentoId();

        assertThat(id.getMedicoId()).isNull();
        assertThat(id.getEstabelecimentoId()).isNull();
    }
}
