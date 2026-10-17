package br.com.sgsm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// Relógio injetável: permite fixar "agora" nos testes (validade das pré-reservas de horário)
@Configuration
public class RelogioConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
