package br.com.sgsm.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * JwtAuthFilter é um OncePerRequestFilter puro: sempre delega ao FilterChain e só popula o
 * SecurityContext/atributos de requisição quando o token Bearer é válido.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    @Mock
    private Claims claims;

    private JwtAuthFilter filter;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthFilter criarFiltro() {
        return new JwtAuthFilter(jwtService, redis);
    }

    @Test
    void deveSeguirCadeiaSemAutenticarQuandoNaoHaHeaderAuthorization() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        filter = criarFiltro();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deveSeguirCadeiaSemAutenticarQuandoHeaderNaoComecaComBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");
        filter = criarFiltro();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void deveResponder401SemSeguirCadeiaQuandoTokenInvalido() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-invalido");
        when(jwtService.tokenValido("token-invalido")).thenReturn(false);
        filter = criarFiltro();

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), any());
        verifyNoInteractions(chain);
        verify(jwtService, never()).extrairClaims(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deveResponder401SemSeguirCadeiaQuandoTokenRevogado() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-revogado");
        when(jwtService.tokenValido("token-revogado")).thenReturn(true);
        when(jwtService.extrairClaims("token-revogado")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-123");
        when(redis.hasKey("blacklist:jti-123")).thenReturn(true);
        filter = criarFiltro();

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), any());
        verifyNoInteractions(chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deveAutenticarEPopularAtributosQuandoTokenValidoComRoles() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(jwtService.tokenValido("token-valido")).thenReturn(true);
        when(jwtService.extrairClaims("token-valido")).thenReturn(claims);
        when(claims.get("roles", List.class)).thenReturn(List.of("MEDICO", "FUNCIONARIO"));
        when(claims.getSubject()).thenReturn("usuario@sgsm.com.br");
        when(claims.get("referenciaId", String.class)).thenReturn("ref-123");
        when(claims.get("perfil", String.class)).thenReturn("MEDICO");
        when(claims.get("email", String.class)).thenReturn("usuario@sgsm.com.br");
        filter = criarFiltro();

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("usuario@sgsm.com.br");
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_MEDICO", "ROLE_FUNCIONARIO");
        verify(request).setAttribute("referenciaId", "ref-123");
        verify(request).setAttribute("perfil", "MEDICO");
        verify(request).setAttribute("email", "usuario@sgsm.com.br");
        verify(chain).doFilter(request, response);
    }

    @Test
    void deveAutenticarComListaDeAuthoritiesVaziaQuandoRolesAusentes() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(jwtService.tokenValido("token-valido")).thenReturn(true);
        when(jwtService.extrairClaims("token-valido")).thenReturn(claims);
        when(claims.get("roles", List.class)).thenReturn(null);
        when(claims.getSubject()).thenReturn("usuario@sgsm.com.br");
        filter = criarFiltro();

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).isEmpty();
        verify(chain).doFilter(request, response);
    }
}
