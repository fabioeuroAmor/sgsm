package br.com.sgsm.dto;

import java.time.LocalDate;

public record CadastrarPacienteRequest(
        String nome,
        String cpf,
        LocalDate dataNascimento,
        String email,
        String telefone,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep,
        Boolean consentimentoLgpd
) {}
