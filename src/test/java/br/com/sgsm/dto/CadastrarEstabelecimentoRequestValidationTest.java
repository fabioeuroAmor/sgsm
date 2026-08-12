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
 * Valida as regras de negócio de CadastrarEstabelecimentoRequest (Bean Validation),
 * sem subir contexto Spring — mesmo caminho que o payload percorre via @Valid no controller.
 */
class CadastrarEstabelecimentoRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    // CNPJ real e matematicamente válido, usado como base nos cenários de sucesso
    private static final String CNPJ_VALIDO = "11.222.333/0001-81";

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private CadastrarEstabelecimentoRequest requestValido(String complemento) {
        return new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@clinicasaolucas.com.br",
                "Avenida Paulista", "1000", complemento, "Bela Vista", "São Paulo", "SP", "01310-100");
    }

    @Test
    void deveAceitarPayloadCompletoValido() {
        assertThat(validator.validate(requestValido(""))).isEmpty();
    }

    @Test
    void deveAceitarComComplementoPreenchido() {
        assertThat(validator.validate(requestValido("Sala 42, bloco B"))).isEmpty();
    }

    @Test
    void deveAceitarNomeComNumeros() {
        var request = new CadastrarEstabelecimentoRequest(
                "7UP", CNPJ_VALIDO, "(11) 4002-8922", "contato@7up.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void deveRejeitarNomeComMenosDeTresCaracteres() {
        var request = new CadastrarEstabelecimentoRequest(
                "AB", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "nome")).isNotEmpty();
    }

    @Test
    void deveRejeitarNomeComCaractereProibido() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica @ São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "nome")).isNotEmpty();
    }

    @Test
    void deveRejeitarCnpjComMascaraErrada() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", "11.222.333/000181", "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        var mensagens = mensagensDoCampo(validator.validate(request), "cnpj");
        assertThat(mensagens).contains("CNPJ deve estar no formato XX.XXX.XXX/XXXX-XX");
    }

    @Test
    void deveRejeitarCnpjComDigitosInvalidos() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", "11.111.111/1111-11", "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        var mensagens = mensagensDoCampo(validator.validate(request), "cnpj");
        assertThat(mensagens).contains("CNPJ contém um número de CNPJ inválido");
    }

    @Test
    void deveRejeitarTelefoneComFormatoErrado() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "11 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "telefone")).isNotEmpty();
    }

    @Test
    void deveRejeitarEmailInvalido() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@clinica",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "email")).isNotEmpty();
    }

    @Test
    void deveRejeitarLogradouroVazio() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "", "1000", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "logradouro")).isNotEmpty();
    }

    @Test
    void deveRejeitarNumeroComCaractereInvalido() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "#123", null, "Bela Vista", "São Paulo", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "numero")).isNotEmpty();
    }

    @Test
    void deveRejeitarComplementoComMaisDeCemCaracteres() {
        var request = requestValido("A".repeat(101));
        assertThat(mensagensDoCampo(validator.validate(request), "complemento")).isNotEmpty();
    }

    @Test
    void deveRejeitarBairroComNumeros() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista 2", "São Paulo", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "bairro")).isNotEmpty();
    }

    @Test
    void deveRejeitarCepComFormatoErrado() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310100");
        assertThat(mensagensDoCampo(validator.validate(request), "cep")).isNotEmpty();
    }

    @Test
    void deveRejeitarCidadeComNumeros() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo 1", "SP", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "cidade")).isNotEmpty();
    }

    @Test
    void deveRejeitarUfInvalida() {
        var request = new CadastrarEstabelecimentoRequest(
                "Clínica São Lucas", CNPJ_VALIDO, "(11) 4002-8922", "contato@teste.com.br",
                "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "XX", "01310-100");
        assertThat(mensagensDoCampo(validator.validate(request), "uf")).isNotEmpty();
    }

    private Set<String> mensagensDoCampo(Set<ConstraintViolation<CadastrarEstabelecimentoRequest>> violacoes, String campo) {
        return violacoes.stream()
                .filter(v -> v.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
