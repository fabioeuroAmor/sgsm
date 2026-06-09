package br.com.sgsm.dto;

public record CadastrarMedicoRequest(
        String nome,
        String crm,
        String crmUf,
        String especialidade,
        String email,
        String telefone
) {}
