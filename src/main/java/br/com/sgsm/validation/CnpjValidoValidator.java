package br.com.sgsm.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

// Valida formato (XX.XXX.XXX/XXXX-XX) e dígitos verificadores do CNPJ,
// reportando uma mensagem distinta para cada tipo de falha.
public class CnpjValidoValidator implements ConstraintValidator<CnpjValido, String> {

    private static final Pattern FORMATO = Pattern.compile("^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$");
    private static final int[] PESOS_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        if (valor == null) {
            return true; // @NotBlank cuida da obrigatoriedade
        }

        if (!FORMATO.matcher(valor).matches()) {
            reportar(context, "CNPJ deve estar no formato XX.XXX.XXX/XXXX-XX");
            return false;
        }

        if (!digitosValidos(valor)) {
            reportar(context, "CNPJ contém um número de CNPJ inválido");
            return false;
        }

        return true;
    }

    private boolean digitosValidos(String cnpjMascarado) {
        String digitos = cnpjMascarado.replaceAll("\\D", "");
        if (digitos.matches("(\\d)\\1{13}")) {
            return false; // todos os dígitos iguais (00.000.000/0000-00, 11.111.111/1111-11, ...)
        }

        int dv1 = calcularDigito(digitos.substring(0, 12), PESOS_DV1);
        int dv2 = calcularDigito(digitos.substring(0, 12) + dv1, PESOS_DV2);

        return digitos.endsWith("" + dv1 + dv2);
    }

    private int calcularDigito(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += Character.getNumericValue(base.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private void reportar(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem).addConstraintViolation();
    }
}
