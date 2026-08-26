package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "log_acesso")
public class LogAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(length = 30)
    private String perfil;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String entidade;

    @Column(name = "entidade_id", nullable = false)
    private UUID entidadeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AcaoAuditoria acao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public LogAcesso() {}

    @PrePersist
    void prePersist() {
        this.criadoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getPerfil() { return perfil; }
    public String getEmail() { return email; }
    public String getEntidade() { return entidade; }
    public UUID getEntidadeId() { return entidadeId; }
    public AcaoAuditoria getAcao() { return acao; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public void setEmail(String email) { this.email = email; }
    public void setEntidade(String entidade) { this.entidade = entidade; }
    public void setEntidadeId(UUID entidadeId) { this.entidadeId = entidadeId; }
    public void setAcao(AcaoAuditoria acao) { this.acao = acao; }
}
