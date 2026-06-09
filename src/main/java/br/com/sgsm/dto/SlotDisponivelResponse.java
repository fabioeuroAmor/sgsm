package br.com.sgsm.dto;

import java.time.OffsetDateTime;

public record SlotDisponivelResponse(
        OffsetDateTime dataHoraInicio,
        OffsetDateTime dataHoraFim,
        int duracaoMinutos
) {}
