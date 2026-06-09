package br.com.sgsm.dto;

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
public class MedicoResponse {

    private UUID id;
    private String nome;
    private String crm;
    private String crmUf;
    private String especialidade;
    private String email;
    private String telefone;
    private Boolean ativo;
    private OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;
}
