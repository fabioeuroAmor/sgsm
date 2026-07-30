package br.com.sgsm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private static final Duration EXPIRACAO_TOKEN_SERVICO = Duration.ofMinutes(2);

    private final SecretKey chave;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.chave = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean tokenValido(String token) {
        try {
            extrairClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Token servico-a-servico (sgsm -> sgsm-ia), assinado com o mesmo segredo compartilhado
    // do sgsm-auth. Vida curta: usado uma unica vez, na chamada a /ia/chat, e descartado.
    // Carrega o perfil/referenciaId ja resolvidos (ex.: PACIENTE identificado por telefone
    // no canal WhatsApp) para que a Camada 6 do sgsm-ia restrinja a busca no Milvus.
    public String gerarTokenServico(String referenciaId, String perfil, String email) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + EXPIRACAO_TOKEN_SERVICO.toMillis());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(referenciaId)
                .claim("email", email)
                .claim("perfil", perfil)
                .claim("referenciaId", referenciaId)
                .claim("roles", List.of(perfil))
                .claim("permissions", List.of())
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chave)
                .compact();
    }
}
