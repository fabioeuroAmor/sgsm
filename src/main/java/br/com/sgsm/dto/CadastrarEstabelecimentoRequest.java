package br.com.sgsm.dto;

public record CadastrarEstabelecimentoRequest(
        String nome,
        String cnpj,
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
