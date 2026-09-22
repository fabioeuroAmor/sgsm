package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdministradorEstabelecimentoId é a chave composta (@EmbeddedId) de
 * AdministradorEstabelecimento. Seu contrato de equals/hashCode é usado pelo Hibernate/JPA
 * para identidade de entidade — por isso merece verificação explícita (comportamento real,
 * não apenas getters triviais).
 */
class AdministradorEstabelecimentoIdTest {

    @Test
    void doisIdsComMesmosValoresDevemSerIguais() {
        UUID administradorId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();

        var id1 = new AdministradorEstabelecimentoId(administradorId, estabelecimentoId);
        var id2 = new AdministradorEstabelecimentoId(administradorId, estabelecimentoId);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void deveSerIgualASiMesmo() {
        var id = new AdministradorEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(id).isEqualTo(id);
    }

    @Test
    void idsComValoresDiferentesNaoDevemSerIguais() {
        var id1 = new AdministradorEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());
        var id2 = new AdministradorEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void naoDeveSerIgualAObjetoDeOutroTipo() {
        var id = new AdministradorEstabelecimentoId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(id).isNotEqualTo("não é um AdministradorEstabelecimentoId");
    }

    @Test
    void construtorPadraoDeveCriarIdVazio() {
        var id = new AdministradorEstabelecimentoId();

        assertThat(id.getAdministradorId()).isNull();
        assertThat(id.getEstabelecimentoId()).isNull();
    }
}
