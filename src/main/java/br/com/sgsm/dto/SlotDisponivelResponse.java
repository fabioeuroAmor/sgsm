package br.com.sgsm.dto;

import java.time.OffsetDateTime;

// reservadoPorVoce: o horário está pré-reservado pelo próprio usuário (horários pré-reservados
// por outros não aparecem na lista)
public record SlotDisponivelResponse(
        OffsetDateTime dataHoraInicio,
        OffsetDateTime dataHoraFim,
        int duracaoMinutos,
        boolean reservadoPorVoce
) {
    public SlotDisponivelResponse(OffsetDateTime dataHoraInicio, OffsetDateTime dataHoraFim, int duracaoMinutos) {
        this(dataHoraInicio, dataHoraFim, duracaoMinutos, false);
    }
}
