package br.com.sgsm.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServicoMedicoResponse {

    private UUID id;
    private UUID medicoId;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private Integer duracaoMinutos;
    private Boolean domiciliar;
    private BigDecimal taxaDeslocamento;
    private Boolean ativo;
    private OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;
}
