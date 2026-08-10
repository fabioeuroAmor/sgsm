package br.com.sgsm.dto;

import java.util.UUID;

public record CadastrarFuncionarioRequest(
        String nome,
        String cpf,
        String email,
        String telefone,
        String cargo,
        UUID estabelecimentoId
) {}
