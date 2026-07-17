package br.com.sgsm.dto;

public record AtualizarFuncionarioRequest(
        String nome,
        String email,
        String telefone,
        String cargo
) {}
