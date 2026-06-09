package br.com.sgsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MedicoEstabelecimentoId implements Serializable {

    @Column(name = "medico_id")
    private UUID medicoId;

    @Column(name = "estabelecimento_id")
    private UUID estabelecimentoId;

    public MedicoEstabelecimentoId() {}

    public MedicoEstabelecimentoId(UUID medicoId, UUID estabelecimentoId) {
        this.medicoId = medicoId;
        this.estabelecimentoId = estabelecimentoId;
    }

    public UUID getMedicoId() { return medicoId; }
    public UUID getEstabelecimentoId() { return estabelecimentoId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicoEstabelecimentoId that)) return false;
        return Objects.equals(medicoId, that.medicoId) &&
               Objects.equals(estabelecimentoId, that.estabelecimentoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(medicoId, estabelecimentoId);
    }
}
