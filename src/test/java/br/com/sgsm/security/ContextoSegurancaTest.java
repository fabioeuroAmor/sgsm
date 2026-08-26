package br.com.sgsm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ContextoSeguranca lê os atributos de requisição (populados pelo JwtAuthFilter) e as
 * authorities do SecurityContext. Os testes manipulam diretamente RequestContextHolder e
 * SecurityContextHolder (ThreadLocals) e limpam o estado ao final para garantir isolamento (FIRST).
 */
class ContextoSegurancaTest {

    private final ContextoSeguranca contexto = new ContextoSeguranca();

    @AfterEach
    void limparContextos() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void configurarRequestAttributes(String perfil, String referenciaId) {
        configurarRequestAttributes(perfil, referenciaId, null);
    }

    private void configurarRequestAttributes(String perfil, String referenciaId, String email) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("perfil")).thenReturn(perfil);
        when(request.getAttribute("referenciaId")).thenReturn(referenciaId);
        when(request.getAttribute("email")).thenReturn(email);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void configurarAutenticacao(String... roles) {
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        var auth = new UsernamePasswordAuthenticationToken("user", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void deveRetornarPerfilDoAtributoDeRequisicao() {
        configurarRequestAttributes("MEDICO", null);

        assertThat(contexto.getPerfil()).isEqualTo("MEDICO");
    }

    @Test
    void deveRetornarNuloQuandoNaoHaContextoDeRequisicao() {
        RequestContextHolder.resetRequestAttributes();

        assertThat(contexto.getPerfil()).isNull();
    }

    @Test
    void deveRetornarEmailDoAtributoDeRequisicao() {
        configurarRequestAttributes(null, null, "usuario@teste.com");

        assertThat(contexto.getEmail()).isEqualTo("usuario@teste.com");
    }

    @Test
    void deveRetornarEmailNuloQuandoAtributoAusente() {
        configurarRequestAttributes(null, null);

        assertThat(contexto.getEmail()).isNull();
    }

    @Test
    void deveConverterReferenciaIdParaUuidQuandoPresente() {
        UUID id = UUID.randomUUID();
        configurarRequestAttributes(null, id.toString());

        assertThat(contexto.getReferenciaId()).isEqualTo(id);
    }

    @Test
    void deveRetornarReferenciaIdNulaQuandoAtributoAusente() {
        configurarRequestAttributes(null, null);

        assertThat(contexto.getReferenciaId()).isNull();
    }

    @Test
    void isDesenvolvedorDeveRetornarTrueQuandoRolePresente() {
        configurarAutenticacao("ROLE_DESENVOLVEDOR");

        assertThat(contexto.isDesenvolvedor()).isTrue();
    }

    @Test
    void isFuncionarioDeveRetornarTrueQuandoRolePresente() {
        configurarAutenticacao("ROLE_FUNCIONARIO");

        assertThat(contexto.isFuncionario()).isTrue();
    }

    @Test
    void isMedicoDeveRetornarTrueQuandoRolePresente() {
        configurarAutenticacao("ROLE_MEDICO");

        assertThat(contexto.isMedico()).isTrue();
    }

    @Test
    void isPacienteDeveRetornarTrueQuandoRolePresente() {
        configurarAutenticacao("ROLE_PACIENTE");

        assertThat(contexto.isPaciente()).isTrue();
    }

    @Test
    void isPacienteDeveRetornarFalseQuandoRoleAusente() {
        configurarAutenticacao("ROLE_MEDICO");

        assertThat(contexto.isPaciente()).isFalse();
    }

    @Test
    void isMedicoDeveRetornarFalseQuandoNaoHaAutenticacao() {
        SecurityContextHolder.clearContext();

        assertThat(contexto.isMedico()).isFalse();
    }
}
