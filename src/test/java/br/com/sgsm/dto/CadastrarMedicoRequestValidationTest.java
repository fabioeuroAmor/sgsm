package br.com.sgsm.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida as regras de negócio de CadastrarMedicoRequest (Bean Validation),
 * sem subir contexto Spring — mesmo caminho que o payload percorre via @Valid no controller.
 */
class CadastrarMedicoRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private CadastrarMedicoRequest requestValido(String telefone) {
        return new CadastrarMedicoRequest(
                "Dr. João Silva", "123456", "SP", "Cardiologia", "joao@clinica.com", telefone);
    }

    @Test
    void deveAceitarPayloadCompletoValido() {
        var violacoes = validator.validate(requestValido("(11) 99999-0000"));
        assertThat(violacoes).isEmpty();
    }

    @Test
    void deveAceitarSemTelefonePorSerOpcional() {
        var violacoes = validator.validate(requestValido(null));
        assertThat(violacoes).isEmpty();
    }

    @Test
    void deveRejeitarNomeAusente() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest(null, "123456", "SP", "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "nome")).isNotEmpty();
    }

    @Test
    void deveRejeitarNomeComMenosDeTresCaracteres() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Jo", "123456", "SP", "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "nome")).isNotEmpty();
    }

    @Test
    void deveRejeitarNomeComApenasNumeros() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("123456", "123456", "SP", "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "nome"))
                .contains("Nome não pode conter apenas números");
    }

    @Test
    void deveRejeitarCrmAusente() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", null, "SP", "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "crm")).isNotEmpty();
    }

    @Test
    void deveRejeitarCrmComLetras() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "12A456", "SP", "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "crm")).isNotEmpty();
    }

    @Test
    void deveRejeitarCrmForaDoIntervaloDeDigitos() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "123", "SP", "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "crm")).isNotEmpty();
    }

    @Test
    void deveRejeitarUfAusente() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "123456", null, "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "crmUf")).isNotEmpty();
    }

    @Test
    void deveRejeitarUfInvalida() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "123456", "XX", "Cardiologia", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "crmUf")).contains("UF do CRM inválida");
    }

    @Test
    void deveRejeitarEspecialidadeAusente() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "123456", "SP", null, "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "especialidade")).isNotEmpty();
    }

    @Test
    void deveRejeitarEspecialidadeIgualASelecione() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "123456", "SP", "Selecione...", "joao@clinica.com", null));
        assertThat(mensagensDoCampo(violacoes, "especialidade")).isNotEmpty();
    }

    @Test
    void deveRejeitarEmailAusente() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "123456", "SP", "Cardiologia", null, null));
        assertThat(mensagensDoCampo(violacoes, "email")).isNotEmpty();
    }

    @Test
    void deveRejeitarEmailComFormatoInvalido() {
        var violacoes = validator.validate(
                new CadastrarMedicoRequest("Dr. João Silva", "123456", "SP", "Cardiologia", "email-invalido", null));
        assertThat(mensagensDoCampo(violacoes, "email")).isNotEmpty();
    }

    @Test
    void deveRejeitarTelefoneComFormatoInvalido() {
        var violacoes = validator.validate(requestValido("(11) 9999-0000"));
        assertThat(mensagensDoCampo(violacoes, "telefone")).isNotEmpty();
    }

    @Test
    void deveRejeitarTelefoneSemMascaraComQuantidadeErradaDeDigitos() {
        var violacoes = validator.validate(requestValido("1199990000"));
        assertThat(mensagensDoCampo(violacoes, "telefone")).isNotEmpty();
    }

    @Test
    void deveAceitarTelefoneSemMascaraComOnzeDigitos() {
        var violacoes = validator.validate(requestValido("11999990000"));
        assertThat(violacoes).isEmpty();
    }

    private Set<String> mensagensDoCampo(Set<ConstraintViolation<CadastrarMedicoRequest>> violacoes, String campo) {
        return violacoes.stream()
                .filter(v -> v.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
