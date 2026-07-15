package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BloqueioAgenda é uma entidade somente leitura após a criação (populada apenas pelo
 * Hibernate via reflexão, sem setters públicos). Os testes usam reflexão para simular essa
 * hidratação e validar que os getters expõem corretamente o estado persistido, além do
 * callback @PrePersist que carimba a data de criação.
 */
class BloqueioAgendaTest {

    private void setField(Object target, String nome, Object valor) throws Exception {
        Field field = BloqueioAgenda.class.getDeclaredField(nome);
        field.setAccessible(true);
        field.set(target, valor);
    }

    @Test
    void prePersistDeveDefinirDataDeCriacao() {
        var bloqueio = new BloqueioAgenda();

        bloqueio.prePersist();

        assertThat(bloqueio.getCriadoEm()).isNotNull();
    }

    @Test
    void deveExporEstadoPersistidoAtravesDosGetters() throws Exception {
        var bloqueio = new BloqueioAgenda();
        UUID id = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        UUID estabelecimentoId = UUID.randomUUID();
        OffsetDateTime inicio = OffsetDateTime.now();
        OffsetDateTime fim = inicio.plusHours(1);

        setField(bloqueio, "id", id);
        setField(bloqueio, "medicoId", medicoId);
        setField(bloqueio, "estabelecimentoId", estabelecimentoId);
        setField(bloqueio, "tipo", TipoBloqueio.FERIAS);
        setField(bloqueio, "dataHoraInicio", inicio);
        setField(bloqueio, "dataHoraFim", fim);
        setField(bloqueio, "motivo", "Férias médicas");

        assertThat(bloqueio.getId()).isEqualTo(id);
        assertThat(bloqueio.getMedicoId()).isEqualTo(medicoId);
        assertThat(bloqueio.getEstabelecimentoId()).isEqualTo(estabelecimentoId);
        assertThat(bloqueio.getTipo()).isEqualTo(TipoBloqueio.FERIAS);
        assertThat(bloqueio.getDataHoraInicio()).isEqualTo(inicio);
        assertThat(bloqueio.getDataHoraFim()).isEqualTo(fim);
        assertThat(bloqueio.getMotivo()).isEqualTo("Férias médicas");
    }
}
