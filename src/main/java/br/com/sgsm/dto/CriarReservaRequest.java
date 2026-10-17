package br.com.sgsm.dto;

import br.com.sgsm.domain.TipoAgendamento;

import java.time.OffsetDateTime;
import java.util.UUID;

// Pré-reserva de um horário enquanto a secretária (ou o paciente, no chatbot/WhatsApp) finaliza
public record CriarReservaRequest(
        UUID servicoMedicoId,
        UUID estabelecimentoId,
        TipoAgendamento tipo,
        OffsetDateTime dataHoraInicio
) {}
