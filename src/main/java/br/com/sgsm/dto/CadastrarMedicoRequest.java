package br.com.sgsm.dto;

import java.util.UUID;

// estabelecimentoId: clínica a que o médico já nasce vinculado. FUNCIONARIO (uma clínica) e
// ADMIN_ESTABELECIMENTO com uma só clínica não precisam informar; admin com várias escolhe uma
public record CadastrarMedicoRequest(
        String nome,
        String crm,
        String crmUf,
        String especialidade,
        String email,
        String telefone,
        UUID estabelecimentoId
) {
    public CadastrarMedicoRequest(String nome, String crm, String crmUf, String especialidade,
                                  String email, String telefone) {
        this(nome, crm, crmUf, especialidade, email, telefone, null);
    }
}
