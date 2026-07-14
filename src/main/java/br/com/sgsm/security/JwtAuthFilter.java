package br.com.sgsm.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (!jwtService.tokenValido(token)) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims = jwtService.extrairClaims(token);

        List<String> roles = claims.get("roles", List.class);
        List<SimpleGrantedAuthority> authorities = roles == null ? List.of() :
                roles.stream()
                     .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                     .toList();

        var auth = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, authorities);

        // Disponibiliza contexto de segurança para os Services
        request.setAttribute("referenciaId", claims.get("referenciaId", String.class));
        request.setAttribute("perfil", claims.get("perfil", String.class));
        request.setAttribute("email", claims.get("email", String.class));

        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
