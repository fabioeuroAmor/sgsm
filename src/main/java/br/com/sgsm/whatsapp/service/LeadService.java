package br.com.sgsm.whatsapp.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Telefone de WhatsApp nao cadastrado como paciente vira lead (origem TELEFONE, status NOVO) —
// via JDBC direto no schema crm, mesmo padrao de ContatoPacienteService.
@Service
public class LeadService {

    private final JdbcTemplate jdbc;

    public LeadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Idempotente por telefone: se ja existe um lead com esse telefone, so retorna o id
    // existente (nao duplica a cada mensagem nova do mesmo visitante).
    public UUID criarOuObterExistente(String telefone, String nomeSugerido) {
        List<String> existentes = jdbc.queryForList(
                "SELECT id::text FROM crm.lead WHERE telefone = ? ORDER BY criado_em DESC LIMIT 1",
                String.class, telefone);
        if (!existentes.isEmpty()) {
            return UUID.fromString(existentes.get(0));
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm.lead (id, nome, telefone, origem, status)
                VALUES (?::uuid, ?, ?, 'TELEFONE'::crm.origem_lead, 'NOVO'::crm.status_lead)
                """, id.toString(), nomeSugerido, telefone);
        return id;
    }
}
