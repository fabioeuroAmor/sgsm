package br.com.sgsm.dto;

public record CadastrarAdministradorRequest(
        String nome,
        String cpf,
        String email,
        String telefone
) {}
