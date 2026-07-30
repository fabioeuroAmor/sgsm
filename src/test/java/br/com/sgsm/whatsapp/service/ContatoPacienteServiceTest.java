package br.com.sgsm.whatsapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContatoPacienteServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private ContatoPacienteService service;

    @BeforeEach
    void setUp() {
        service = new ContatoPacienteService(jdbc);
    }

    @Test
    void registrarEntradaGravaDirecaoEntrada() {
        service.registrarEntrada("paciente-1", "Qual meu histórico?");

        verify(jdbc).update(contains("INSERT INTO crm.contato_paciente"),
                eq("paciente-1"), eq("ENTRADA"), eq("Qual meu histórico?"));
    }

    @Test
    void registrarSaidaGravaDirecaoSaida() {
        service.registrarSaida("paciente-1", "Você tem 3 consultas concluídas.");

        verify(jdbc).update(contains("INSERT INTO crm.contato_paciente"),
                eq("paciente-1"), eq("SAIDA"), eq("Você tem 3 consultas concluídas."));
    }
}
