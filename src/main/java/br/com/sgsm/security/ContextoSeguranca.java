package br.com.sgsm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class ContextoSeguranca {

    public String getPerfil() {
        return atributoRequest("perfil");
    }

    public String getEmail() {
        return atributoRequest("email");
    }

    public UUID getReferenciaId() {
        String val = atributoRequest("referenciaId");
        return val != null ? UUID.fromString(val) : null;
    }

    public boolean isDesenvolvedor() {
        return temRole("ROLE_DESENVOLVEDOR");
    }

    public boolean isFuncionario() {
        return temRole("ROLE_FUNCIONARIO");
    }

    public boolean isMedico() {
        return temRole("ROLE_MEDICO");
    }

    public boolean isPaciente() {
        return temRole("ROLE_PACIENTE");
    }

    private boolean temRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    private String atributoRequest(String nome) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            Object val = sra.getRequest().getAttribute(nome);
            return val != null ? val.toString() : null;
        }
        return null;
    }
}
