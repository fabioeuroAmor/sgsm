package br.com.sgsm.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RelogioConfigTest {

    @Test
    void relogioUsaOFusoDoSgsm() {
        assertThat(new RelogioConfig().clock().getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }
}
