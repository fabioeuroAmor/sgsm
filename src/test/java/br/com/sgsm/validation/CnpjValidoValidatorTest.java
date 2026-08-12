package br.com.sgsm.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CnpjValidoValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    private CnpjValidoValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CnpjValidoValidator();
        org.mockito.Mockito.when(context.buildConstraintViolationWithTemplate(org.mockito.Mockito.anyString()))
                .thenReturn(builder);
    }

    @Test
    void deveAceitarNuloPoisNotBlankCuidaDaObrigatoriedade() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    void deveAceitarCnpjRealEValido() {
        assertThat(validator.isValid("11.222.333/0001-81", context)).isTrue();
    }

    @Test
    void deveRejeitarCnpjSemMascara() {
        assertThat(validator.isValid("11222333000181", context)).isFalse();
    }

    @Test
    void deveRejeitarCnpjComDigitosVerificadoresInvalidos() {
        assertThat(validator.isValid("11.222.333/0001-00", context)).isFalse();
    }

    @Test
    void deveRejeitarCnpjComTodosOsDigitosIguais() {
        assertThat(validator.isValid("11.111.111/1111-11", context)).isFalse();
    }

    @Test
    void deveRejeitarCnpjZerado() {
        assertThat(validator.isValid("00.000.000/0000-00", context)).isFalse();
    }
}
