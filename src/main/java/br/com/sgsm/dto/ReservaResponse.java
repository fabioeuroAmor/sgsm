package br.com.sgsm.dto;

import java.time.OffsetDateTime;

// reservado=false: o Redis estava indisponível e a pré-reserva não foi feita (falha aberta) —
// o fluxo segue; a constraint do banco continua impedindo dois agendamentos no mesmo horário
public record ReservaResponse(
        String token,
        OffsetDateTime dataHoraInicio,
        OffsetDateTime dataHoraFim,
        OffsetDateTime expiraEm,
        OffsetDateTime renovavelAte,
        boolean reservado
) {}
