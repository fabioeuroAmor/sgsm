package br.com.sgsm.whatsapp.util;

import java.util.List;

// Normaliza numeros de telefone vindos do WhatsApp (formato E.164 com DDI, ex. 5561999998888)
// contra o que pode estar cadastrado em sgsm.paciente.telefone (formato livre, com ou sem DDI).
public final class TelefoneNormalizador {

    private TelefoneNormalizador() {}

    public static String apenasDigitos(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("\\D", "");
    }

    // Gera as variantes plausiveis (com e sem DDI 55) para casar contra o telefone
    // cadastrado, que pode ou nao incluir o DDI dependendo de como foi digitado no cadastro.
    public static List<String> variantes(String telefone) {
        String digitos = apenasDigitos(telefone);
        if (digitos.isEmpty()) {
            return List.of();
        }
        if (digitos.startsWith("55") && digitos.length() > 11) {
            return List.of(digitos, digitos.substring(2));
        }
        return List.of(digitos, "55" + digitos);
    }
}
