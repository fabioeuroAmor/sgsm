package br.com.sgsm.dto;

import br.com.sgsm.domain.StatusAgendamento;

public record AtualizarStatusAgendamentoRequest(StatusAgendamento status, String localizacaoMedico) {}
