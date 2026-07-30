package br.com.sgsm.whatsapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private LeadService service;

    @BeforeEach
    void setUp() {
        service = new LeadService(jdbc);
    }

    @Test
    void criaNovoLeadQuandoTelefoneNaoExiste() {
        when(jdbc.queryForList(contains("SELECT id::text FROM crm.lead"), eq(String.class), eq("5561999998888")))
                .thenReturn(List.of());

        UUID id = service.criarOuObterExistente("5561999998888", "Contato WhatsApp");

        assertThat(id).isNotNull();
        verify(jdbc).update(contains("INSERT INTO crm.lead"),
                eq(id.toString()), eq("Contato WhatsApp"), eq("5561999998888"));
    }

    @Test
    void reaproveitaLeadExistenteSemDuplicar() {
        UUID existente = UUID.randomUUID();
        when(jdbc.queryForList(contains("SELECT id::text FROM crm.lead"), eq(String.class), eq("5561999998888")))
                .thenReturn(List.of(existente.toString()));

        UUID id = service.criarOuObterExistente("5561999998888", "Contato WhatsApp");

        assertThat(id).isEqualTo(existente);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
