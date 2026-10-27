package br.com.sgsm.dto;

import java.util.List;
import java.util.Map;

/** LGPD (item 3.2 do compliance) — portabilidade de dados: tudo que o SGSM guarda sobre o titular. */
public record PacienteExportacaoResponse(
        PacienteResponse paciente,
        List<AgendamentoResponse> agendamentos,
        // clínicas por onde o titular chegou (V015) e consentimentos LGPD por clínica e canal (V016)
        List<Map<String, Object>> clinicas,
        List<Map<String, Object>> consentimentos
) {
    public PacienteExportacaoResponse(PacienteResponse paciente, List<AgendamentoResponse> agendamentos) {
        this(paciente, agendamentos, List.of(), List.of());
    }
}
