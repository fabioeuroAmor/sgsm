package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa as regras de negócio embutidas no ciclo de vida JPA de Agendamento: status inicial
 * PENDENTE e tipo padrão PRESENCIAL quando não informado.
 */
class AgendamentoTest {

    @Test
    void prePersistDeveDefinirStatusPendenteETipoPresencialQuandoNaoInformado() {
        var agendamento = new Agendamento();

        agendamento.prePersist();

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
        assertThat(agendamento.getTipo()).isEqualTo(TipoAgendamento.PRESENCIAL);
        assertThat(agendamento.getCriadoEm()).isNotNull();
        assertThat(agendamento.getAtualizadoEm()).isNotNull();
    }

    @Test
    void prePersistNaoDeveSobrescreverTipoQuandoJaInformado() {
        var agendamento = new Agendamento();
        agendamento.setTipo(TipoAgendamento.DOMICILIAR);

        agendamento.prePersist();

        assertThat(agendamento.getTipo()).isEqualTo(TipoAgendamento.DOMICILIAR);
    }

    @Test
    void preUpdateDeveAtualizarDataDeAtualizacao() {
        var agendamento = new Agendamento();
        agendamento.prePersist();

        agendamento.preUpdate();

        assertThat(agendamento.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarPagamentoIdInformado() {
        var agendamento = new Agendamento();
        UUID pagamentoId = UUID.randomUUID();

        agendamento.setPagamentoId(pagamentoId);

        assertThat(agendamento.getPagamentoId()).isEqualTo(pagamentoId);
    }
}
