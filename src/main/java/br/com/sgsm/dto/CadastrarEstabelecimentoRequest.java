package br.com.sgsm.dto;

import br.com.sgsm.validation.CnpjValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CadastrarEstabelecimentoRequest(

        @NotBlank(message = "Nome deve ter entre 3 e 100 caracteres e conter apenas letras, números, espaços, pontos, vírgulas, hífens, & ou +")
        @Pattern(
                regexp = "^(?=.{3,100}$)[A-Za-zÀ-ÿ0-9\\s.,;:&+\\-']+$",
                message = "Nome deve ter entre 3 e 100 caracteres e conter apenas letras, números, espaços, pontos, vírgulas, hífens, & ou +"
        )
        String nome,

        @NotBlank(message = "CNPJ deve estar no formato XX.XXX.XXX/XXXX-XX")
        @CnpjValido
        String cnpj,

        @NotBlank(message = "Telefone deve estar no formato (XX) XXXX-XXXX ou (XX) XXXXX-XXXX")
        @Pattern(
                regexp = "^\\(\\d{2}\\) \\d{4,5}-\\d{4}$",
                message = "Telefone deve estar no formato (XX) XXXX-XXXX ou (XX) XXXXX-XXXX"
        )
        String telefone,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "E-mail inválido")
        String email,

        @NotBlank(message = "Logradouro deve ter entre 3 e 200 caracteres e conter apenas letras, números, espaços, pontos, vírgulas ou hífens")
        @Pattern(
                regexp = "^(?=.{3,200}$)[A-Za-zÀ-ÿ0-9\\s.,\\-]+$",
                message = "Logradouro deve ter entre 3 e 200 caracteres e conter apenas letras, números, espaços, pontos, vírgulas ou hífens"
        )
        String logradouro,

        @NotBlank(message = "Número deve ser preenchido com o número, letra ou 'S/N' (ex: 123, S/N, A1)")
        @Pattern(
                regexp = "^[A-Za-z0-9/]+$",
                message = "Número deve ser preenchido com o número, letra ou 'S/N' (ex: 123, S/N, A1)"
        )
        String numero,

        // Opcional: null/string vazia são ignorados; se preenchido, só valida o tamanho máximo.
        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
        String complemento,

        @NotBlank(message = "Bairro deve ter entre 2 e 100 caracteres e conter apenas letras, espaços, pontos ou hífens")
        @Pattern(
                regexp = "^(?=.{2,100}$)[A-Za-zÀ-ÿ\\s.\\-]+$",
                message = "Bairro deve ter entre 2 e 100 caracteres e conter apenas letras, espaços, pontos ou hífens"
        )
        String bairro,

        @NotBlank(message = "Cidade deve ter entre 2 e 100 caracteres e conter apenas letras, espaços, pontos ou hífens")
        @Pattern(
                regexp = "^(?=.{2,100}$)[A-Za-zÀ-ÿ\\s.\\-]+$",
                message = "Cidade deve ter entre 2 e 100 caracteres e conter apenas letras, espaços, pontos ou hífens"
        )
        String cidade,

        @NotBlank(message = "UF inválida")
        @Pattern(
                regexp = "^(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)$",
                message = "UF deve conter uma sigla de estado brasileiro válida (ex: SP, RJ, MG)"
        )
        String uf,

        @NotBlank(message = "CEP deve estar no formato XXXXX-XXX")
        @Pattern(regexp = "^\\d{5}-\\d{3}$", message = "CEP deve estar no formato XXXXX-XXX")
        String cep
) {}
