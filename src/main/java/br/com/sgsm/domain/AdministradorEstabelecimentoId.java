package br.com.sgsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AdministradorEstabelecimentoId implements Serializable {

    @Column(name = "administrador_id")
    private UUID administradorId;

    @Column(name = "estabelecimento_id")
    private UUID estabelecimentoId;

    public AdministradorEstabelecimentoId() {}

    public AdministradorEstabelecimentoId(UUID administradorId, UUID estabelecimentoId) {
        this.administradorId = administradorId;
        this.estabelecimentoId = estabelecimentoId;
    }

    public UUID getAdministradorId() { return administradorId; }
    public UUID getEstabelecimentoId() { return estabelecimentoId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdministradorEstabelecimentoId that)) return false;
        return Objects.equals(administradorId, that.administradorId) &&
               Objects.equals(estabelecimentoId, that.estabelecimentoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(administradorId, estabelecimentoId);
    }
}
