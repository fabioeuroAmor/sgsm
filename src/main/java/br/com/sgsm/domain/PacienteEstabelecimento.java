package br.com.sgsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

// Vínculo paciente x clínica (V015): por onde o paciente chegou (WHATSAPP, SITE, CLINICA)
@Entity
@Table(name = "paciente_estabelecimento")
public class PacienteEstabelecimento {

    @EmbeddedId
    private Id id;

    @Column(nullable = false, length = 20)
    private String origem;

    @Column(name = "primeiro_contato_em", nullable = false, updatable = false)
    private OffsetDateTime primeiroContatoEm;

    public PacienteEstabelecimento() {}

    public PacienteEstabelecimento(UUID pacienteId, UUID estabelecimentoId, String origem) {
        this.id = new Id(pacienteId, estabelecimentoId);
        this.origem = origem;
    }

    @PrePersist
    void prePersist() {
        this.primeiroContatoEm = OffsetDateTime.now();
    }

    public Id getId() { return id; }
    public String getOrigem() { return origem; }
    public OffsetDateTime getPrimeiroContatoEm() { return primeiroContatoEm; }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "paciente_id")
        private UUID pacienteId;

        @Column(name = "estabelecimento_id")
        private UUID estabelecimentoId;

        public Id() {}

        public Id(UUID pacienteId, UUID estabelecimentoId) {
            this.pacienteId = pacienteId;
            this.estabelecimentoId = estabelecimentoId;
        }

        public UUID getPacienteId() { return pacienteId; }
        public UUID getEstabelecimentoId() { return estabelecimentoId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id outro && Objects.equals(pacienteId, outro.pacienteId)
                    && Objects.equals(estabelecimentoId, outro.estabelecimentoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pacienteId, estabelecimentoId);
        }
    }
}
