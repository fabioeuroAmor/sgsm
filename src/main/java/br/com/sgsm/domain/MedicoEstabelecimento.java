package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "medico_estabelecimento")
public class MedicoEstabelecimento {

    @EmbeddedId
    private MedicoEstabelecimentoId id;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public MedicoEstabelecimento() {}

    public MedicoEstabelecimento(java.util.UUID medicoId, java.util.UUID estabelecimentoId) {
        this.id = new MedicoEstabelecimentoId(medicoId, estabelecimentoId);
    }

    @PrePersist
    void prePersist() {
        this.ativo = true;
        this.criadoEm = OffsetDateTime.now();
    }

    public MedicoEstabelecimentoId getId() { return id; }
    public Boolean getAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
