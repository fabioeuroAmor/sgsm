package br.com.sgsm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtService encapsula assinatura/validação HS256 via jjwt. Os testes usam a mesma chave
 * secreta configurada no service para gerar tokens de teste, evitando qualquer chamada de rede.
 */
class JwtServiceTest {

    private static final String SECRET = "sgsm-chave-de-teste-unitario-com-tamanho-minimo-de-256-bits-ok";

    private final JwtService jwtService = new JwtService(SECRET);

    private String gerarToken(SecretKey chave, Date expiracao) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("perfil", "MEDICO")
                .claim("referenciaId", UUID.randomUUID().toString())
                .claim("email", "medico@sgsm.com.br")
                .claim("roles", java.util.List.of("MEDICO"))
                .issuedAt(new Date())
                .expiration(expiracao)
                .signWith(chave)
                .compact();
    }

    @Test
    void deveExtrairClaimsDeTokenValido() {
        SecretKey chave = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = gerarToken(chave, Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        Claims claims = jwtService.extrairClaims(token);

        assertThat(claims.get("perfil")).isEqualTo("MEDICO");
        assertThat(claims.get("email")).isEqualTo("medico@sgsm.com.br");
    }

    @Test
    void tokenValidoDeveRetornarTrueParaTokenAssinadoENaoExpirado() {
        SecretKey chave = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = gerarToken(chave, Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        assertThat(jwtService.tokenValido(token)).isTrue();
    }

    @Test
    void tokenValidoDeveRetornarFalseParaTokenExpirado() {
        SecretKey chave = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = gerarToken(chave, Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));

        assertThat(jwtService.tokenValido(token)).isFalse();
    }

    @Test
    void tokenValidoDeveRetornarFalseParaTokenAssinadoComOutraChave() {
        SecretKey outraChave = Keys.hmacShaKeyFor(
                "outra-chave-completamente-diferente-com-256-bits-no-minimo".getBytes(StandardCharsets.UTF_8));
        String token = gerarToken(outraChave, Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        assertThat(jwtService.tokenValido(token)).isFalse();
    }

    @Test
    void tokenValidoDeveRetornarFalseParaTokenMalFormado() {
        assertThat(jwtService.tokenValido("token-invalido-nao-jwt")).isFalse();
    }

    @Test
    void gerarTokenServicoDeveCarregarPerfilEReferenciaId() {
        String pacienteId = UUID.randomUUID().toString();

        String token = jwtService.gerarTokenServico(pacienteId, "PACIENTE", "paciente@sgsm.com.br");
        Claims claims = jwtService.extrairClaims(token);

        assertThat(claims.getSubject()).isEqualTo(pacienteId);
        assertThat(claims.get("perfil")).isEqualTo("PACIENTE");
        assertThat(claims.get("referenciaId")).isEqualTo(pacienteId);
        assertThat(claims.get("email")).isEqualTo("paciente@sgsm.com.br");
        assertThat(claims.get("roles", java.util.List.class)).containsExactly("PACIENTE");
        assertThat(jwtService.tokenValido(token)).isTrue();
    }

    @Test
    void gerarTokenServicoDeveExpirarEmPoucoTempo() {
        String token = jwtService.gerarTokenServico(UUID.randomUUID().toString(), "PACIENTE", "x@sgsm.com.br");

        Claims claims = jwtService.extrairClaims(token);

        long ttlSegundos = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(ttlSegundos).isLessThanOrEqualTo(120);
    }
}
