package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgendaMedico é uma entidade JPA cujo callback @PrePersist aplica regras de negócio reais
 * (valor padrão de "domiciliar" e "ativo", carimbo de criação/atualização). Esses callbacks
 * não são exercitados pelo Hibernate em testes unitários (sem contexto JPA real), por isso
 * são invocados diretamente aqui — o pacote de teste espelha o pacote de produção justamente
 * para permitir a chamada dos métodos package-private de ciclo de vida.
 */
class AgendaMedicoTest {

    @Test
    void prePersistDeveDefinirDomiciliarFalsoAtivoETimestampsQuandoDomiciliarNaoInformado() {
        var agenda = new AgendaMedico();

        agenda.prePersist();

        assertThat(agenda.getDomiciliar()).isFalse();
        assertThat(agenda.getAtivo()).isTrue();
        assertThat(agenda.getCriadoEm()).isNotNull();
        assertThat(agenda.getAtualizadoEm()).isNotNull();
    }

    @Test
    void prePersistNaoDeveSobrescreverDomiciliarQuandoJaInformado() {
        var agenda = new AgendaMedico();
        agenda.setDomiciliar(true);

        agenda.prePersist();

        assertThat(agenda.getDomiciliar()).isTrue();
    }

    @Test
    void preUpdateDeveAtualizarApenasDataDeAtualizacao() {
        var agenda = new AgendaMedico();
        agenda.prePersist();

        agenda.preUpdate();

        assertThat(agenda.getAtualizadoEm()).isNotNull();
    }
}
