package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa as regras de negócio embutidas no ciclo de vida JPA de ServicoMedico: valor padrão de
 * "domiciliar" e ativação automática ao persistir.
 */
class ServicoMedicoTest {

    @Test
    void prePersistDeveDefinirDomiciliarFalsoAtivoETimestampsQuandoDomiciliarNaoInformado() {
        var servico = new ServicoMedico();

        servico.prePersist();

        assertThat(servico.getDomiciliar()).isFalse();
        assertThat(servico.getAtivo()).isTrue();
        assertThat(servico.getCriadoEm()).isNotNull();
        assertThat(servico.getAtualizadoEm()).isNotNull();
    }

    @Test
    void prePersistNaoDeveSobrescreverDomiciliarQuandoJaInformado() {
        var servico = new ServicoMedico();
        servico.setDomiciliar(true);

        servico.prePersist();

        assertThat(servico.getDomiciliar()).isTrue();
    }

    @Test
    void preUpdateDeveAtualizarDataDeAtualizacao() {
        var servico = new ServicoMedico();
        servico.prePersist();

        servico.preUpdate();

        assertThat(servico.getAtualizadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarTaxaDeDeslocamentoInformada() {
        var servico = new ServicoMedico();

        servico.setTaxaDeslocamento(new BigDecimal("15.00"));

        assertThat(servico.getTaxaDeslocamento()).isEqualByComparingTo("15.00");
    }
}
