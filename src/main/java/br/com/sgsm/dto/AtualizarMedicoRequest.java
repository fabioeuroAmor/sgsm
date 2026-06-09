package br.com.sgsm.dto;

public record AtualizarMedicoRequest(
        String nome,
        String especialidade,
        String email,
        String telefone
) {}
