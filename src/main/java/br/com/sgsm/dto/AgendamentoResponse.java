package br.com.sgsm.dto;

import br.com.sgsm.domain.OrigemCancelamento;
import br.com.sgsm.domain.StatusAgendamento;
import br.com.sgsm.domain.TipoAgendamento;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoResponse {

    private UUID id;

    private UUID pacienteId;
    private String pacienteNome;
    private String pacienteEndereco;

    private UUID servicoMedicoId;
    private String servicoMedicoNome;

    private UUID medicoId;
    private String medicoNome;

    private UUID estabelecimentoId;
    private String estabelecimentoNome;
    private String estabelecimentoEndereco;

    private TipoAgendamento tipo;

    private UUID pagamentoId;

    private OffsetDateTime dataHoraInicio;
    private OffsetDateTime dataHoraFim;

    private StatusAgendamento status;
    private String observacoes;
    private OrigemCancelamento origemCancelamento;
    private String motivoCancelamento;
    private String localizacaoMedico;

    private OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;
}
