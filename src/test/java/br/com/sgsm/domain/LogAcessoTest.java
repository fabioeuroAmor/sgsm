package br.com.sgsm.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogAcessoTest {

    @Test
    void prePersistDeveDefinirCriadoEm() {
        var log = new LogAcesso();

        log.prePersist();

        assertThat(log.getCriadoEm()).isNotNull();
    }

    @Test
    void deveArmazenarCamposInformados() {
        var log = new LogAcesso();
        UUID usuarioId = UUID.randomUUID();
        UUID entidadeId = UUID.randomUUID();

        log.setUsuarioId(usuarioId);
        log.setPerfil("PACIENTE");
        log.setEmail("paciente@teste.com");
        log.setEntidade("PACIENTE");
        log.setEntidadeId(entidadeId);
        log.setAcao(AcaoAuditoria.LEITURA);

        assertThat(log.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(log.getPerfil()).isEqualTo("PACIENTE");
        assertThat(log.getEmail()).isEqualTo("paciente@teste.com");
        assertThat(log.getEntidade()).isEqualTo("PACIENTE");
        assertThat(log.getEntidadeId()).isEqualTo(entidadeId);
        assertThat(log.getAcao()).isEqualTo(AcaoAuditoria.LEITURA);
    }
}
