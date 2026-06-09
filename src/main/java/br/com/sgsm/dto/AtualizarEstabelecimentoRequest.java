package br.com.sgsm.dto;

public record AtualizarEstabelecimentoRequest(
        String nome,
        String telefone,
        String email,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep
) {}
