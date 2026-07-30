package br.com.sgsm.whatsapp.service;

import br.com.sgsm.whatsapp.util.TelefoneNormalizador;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Resolve o telefone de quem escreveu no WhatsApp para um paciente ja cadastrado, comparando
// contra sgsm.paciente.telefone normalizado (o cadastro pode ou nao incluir o DDI).
@Service
public class PacienteIdentificacaoService {

    private final JdbcTemplate jdbc;

    public PacienteIdentificacaoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> identificarPacienteId(String telefoneWhatsApp) {
        List<String> variantes = TelefoneNormalizador.variantes(telefoneWhatsApp);
        if (variantes.isEmpty()) {
            return Optional.empty();
        }

        String placeholders = String.join(",", Collections.nCopies(variantes.size(), "?"));
        String sql = "SELECT id::text FROM sgsm.paciente "
                + "WHERE regexp_replace(telefone, '\\D', '', 'g') IN (" + placeholders + ") "
                + "LIMIT 1";

        List<String> encontrados = jdbc.queryForList(sql, String.class, variantes.toArray());
        return encontrados.stream().findFirst();
    }
}
