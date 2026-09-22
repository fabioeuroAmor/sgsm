package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "administrador_estabelecimento")
public class AdministradorEstabelecimento {

    @EmbeddedId
    private AdministradorEstabelecimentoId id;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public AdministradorEstabelecimento() {}

    public AdministradorEstabelecimento(java.util.UUID administradorId, java.util.UUID estabelecimentoId) {
        this.id = new AdministradorEstabelecimentoId(administradorId, estabelecimentoId);
    }

    @PrePersist
    void prePersist() {
        this.ativo = true;
        this.criadoEm = OffsetDateTime.now();
    }

    public AdministradorEstabelecimentoId getId() { return id; }
    public Boolean getAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
