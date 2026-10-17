package br.com.sgsm.dto;

import br.com.sgsm.domain.TipoAgendamento;
import java.time.OffsetDateTime;
import java.util.UUID;

// reservaToken (opcional): pré-reserva do próprio usuário para este horário, consumida ao gravar
public record CadastrarAgendamentoRequest(
        UUID pacienteId,
        UUID servicoMedicoId,
        UUID estabelecimentoId,
        TipoAgendamento tipo,
        OffsetDateTime dataHoraInicio,
        String observacoes,
        String reservaToken
) {
    public CadastrarAgendamentoRequest(UUID pacienteId, UUID servicoMedicoId, UUID estabelecimentoId,
                                       TipoAgendamento tipo, OffsetDateTime dataHoraInicio, String observacoes) {
        this(pacienteId, servicoMedicoId, estabelecimentoId, tipo, dataHoraInicio, observacoes, null);
    }
}
