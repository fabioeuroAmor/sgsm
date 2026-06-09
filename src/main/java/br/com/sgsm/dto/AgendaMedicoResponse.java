package br.com.sgsm.dto;

import br.com.sgsm.domain.DiaSemana;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendaMedicoResponse {

    private UUID id;
    private UUID medicoId;
    private UUID estabelecimentoId;
    private String estabelecimentoNome;
    private DiaSemana diaSemana;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaFim;

    private Integer duracaoSlotMinutos;
    private LocalDate dataVigenciaInicio;
    private LocalDate dataVigenciaFim;
    private Boolean domiciliar;
    private Integer intervaloDeslocamentoMinutos;
    private java.math.BigDecimal raioKm;
    private String cidadeAtendimento;
    private String ufAtendimento;
    private Boolean ativo;
    private OffsetDateTime criadoEm;
}
