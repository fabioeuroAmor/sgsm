package br.com.sgsm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CadastrarMedicoRequest(

        @NotBlank(message = "Nome é obrigatório e deve ter entre 3 e 100 caracteres")
        @Size(min = 3, max = 100, message = "Nome é obrigatório e deve ter entre 3 e 100 caracteres")
        @Pattern(regexp = "^(?!\\d+$).+$", message = "Nome não pode conter apenas números")
        String nome,

        @NotBlank(message = "CRM é obrigatório")
        @Pattern(regexp = "^\\d{4,9}$", message = "CRM deve conter apenas números, entre 4 e 9 dígitos")
        String crm,

        @NotBlank(message = "UF do CRM é obrigatória")
        @Pattern(
                regexp = "^(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)$",
                message = "UF do CRM inválida"
        )
        String crmUf,

        @NotBlank(message = "Especialidade é obrigatória")
        @Pattern(regexp = "^(?!Selecione\\.\\.\\.$).+$", message = "Especialidade é obrigatória")
        String especialidade,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "E-mail inválido")
        String email,

        // Opcional: null/string vazia passam direto; se preenchido, aceita mascarado
        // (XX) XXXXX-XXXX ou 11 dígitos corridos sem máscara.
        @Pattern(
                regexp = "^$|^\\(\\d{2}\\) \\d{5}-\\d{4}$|^\\d{11}$",
                message = "Telefone deve estar no formato (XX) XXXXX-XXXX ou conter 11 dígitos sem máscara"
        )
        String telefone
) {}
