package br.com.sgsm.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CadastrarServicoMedicoRequest(
        UUID medicoId,
        String nome,
        String descricao,
        BigDecimal preco,
        Integer duracaoMinutos,
        Boolean domiciliar,
        BigDecimal taxaDeslocamento
) {}
