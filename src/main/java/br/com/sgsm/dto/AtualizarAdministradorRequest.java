package br.com.sgsm.dto;

public record AtualizarAdministradorRequest(
        String nome,
        String email,
        String telefone
) {}
