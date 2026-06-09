package br.com.sgsm.dto;

import java.math.BigDecimal;

public record AtualizarServicoMedicoRequest(
        String nome,
        String descricao,
        BigDecimal preco,
        Integer duracaoMinutos,
        Boolean domiciliar,
        BigDecimal taxaDeslocamento
) {}
