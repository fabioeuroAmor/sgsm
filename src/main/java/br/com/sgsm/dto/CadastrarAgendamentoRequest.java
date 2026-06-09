package br.com.sgsm.dto;

import br.com.sgsm.domain.TipoAgendamento;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CadastrarAgendamentoRequest(
        UUID pacienteId,
        UUID servicoMedicoId,
        UUID estabelecimentoId,
        TipoAgendamento tipo,
        OffsetDateTime dataHoraInicio,
        String observacoes
) {}
