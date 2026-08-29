package br.com.sgsm.dto;

import java.util.List;

/** LGPD (item 3.2 do compliance) — portabilidade de dados: tudo que o SGSM guarda sobre o titular. */
public record PacienteExportacaoResponse(
        PacienteResponse paciente,
        List<AgendamentoResponse> agendamentos
) {}
