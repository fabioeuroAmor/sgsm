package br.com.sgsm.dto;

import java.time.LocalDate;

// Auto-cadastro público de paciente pelo site: só os dados mínimos (endereço é completado depois,
// já logado). A senha segue direto para o ms-sboot-auth e nunca é gravada no sgsm.
public record AutoCadastroPacienteRequest(
        String nome,
        String cpf,
        LocalDate dataNascimento,
        String email,
        String telefone,
        Boolean consentimentoLgpd,
        String senha
) {
    @Override
    public String toString() {
        return "AutoCadastroPacienteRequest[nome=" + nome + ", email=" + email + ", cpf=****, senha=****]";
    }
}
