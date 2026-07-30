package br.com.sgsm.whatsapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteIdentificacaoServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private PacienteIdentificacaoService service;

    @BeforeEach
    void setUp() {
        service = new PacienteIdentificacaoService(jdbc);
    }

    @Test
    void identificaPacienteQuandoTelefoneBateComCadastro() {
        when(jdbc.queryForList(contains("FROM sgsm.paciente"), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("paciente-1"));

        Optional<String> resultado = service.identificarPacienteId("5561999998888");

        assertThat(resultado).contains("paciente-1");
    }

    @Test
    void retornaVazioQuandoTelefoneNaoCadastrado() {
        when(jdbc.queryForList(contains("FROM sgsm.paciente"), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());

        Optional<String> resultado = service.identificarPacienteId("5561999998888");

        assertThat(resultado).isEmpty();
    }

    @Test
    void retornaVazioQuandoTelefoneInvalido() {
        Optional<String> resultado = service.identificarPacienteId("");

        assertThat(resultado).isEmpty();
    }
}
