package br.com.sgsm.dto;

import br.com.sgsm.domain.DiaSemana;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CadastrarAgendaMedicoRequest(
        UUID medicoId,
        UUID estabelecimentoId,
        DiaSemana diaSemana,
        String horaInicio,
        String horaFim,
        Integer duracaoSlotMinutos,
        LocalDate dataVigenciaInicio,
        LocalDate dataVigenciaFim,
        Boolean domiciliar,
        Integer intervaloDeslocamentoMinutos,
        BigDecimal raioKm,
        String cidadeAtendimento,
        String ufAtendimento
) {}
