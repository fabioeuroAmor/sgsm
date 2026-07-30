package br.com.sgsm.whatsapp.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Registra o historico de conversas de WhatsApp em crm.contato_paciente. Acesso via JDBC direto
// (mesmo padrao usado pelo sgsm-ia para o schema crm — ver CLAUDE.md), sem entidade JPA: o sgsm
// so grava aqui, nao possui o dominio de CRM.
@Service
public class ContatoPacienteService {

    private final JdbcTemplate jdbc;

    public ContatoPacienteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrarEntrada(String pacienteId, String descricao) {
        registrar(pacienteId, "ENTRADA", descricao);
    }

    public void registrarSaida(String pacienteId, String descricao) {
        registrar(pacienteId, "SAIDA", descricao);
    }

    private void registrar(String pacienteId, String direcao, String descricao) {
        jdbc.update("""
                INSERT INTO crm.contato_paciente (paciente_id, tipo, direcao, descricao)
                VALUES (?::uuid, 'WHATSAPP'::crm.tipo_contato, ?::crm.direcao_contato, ?)
                """, pacienteId, direcao, descricao);
    }
}
