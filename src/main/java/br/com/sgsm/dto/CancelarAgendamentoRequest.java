package br.com.sgsm.dto;

import br.com.sgsm.domain.OrigemCancelamento;

public record CancelarAgendamentoRequest(
        OrigemCancelamento origemCancelamento,
        String motivoCancelamento
) {}
