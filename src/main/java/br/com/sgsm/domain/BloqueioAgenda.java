package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bloqueio_agenda")
public class BloqueioAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "estabelecimento_id")
    private UUID estabelecimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoBloqueio tipo;

    @Column(name = "data_hora_inicio", nullable = false)
    private OffsetDateTime dataHoraInicio;

    @Column(name = "data_hora_fim", nullable = false)
    private OffsetDateTime dataHoraFim;

    @Column(length = 300)
    private String motivo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public BloqueioAgenda() {}

    @PrePersist
    void prePersist() {
        this.criadoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getMedicoId() { return medicoId; }
    public UUID getEstabelecimentoId() { return estabelecimentoId; }
    public TipoBloqueio getTipo() { return tipo; }
    public OffsetDateTime getDataHoraInicio() { return dataHoraInicio; }
    public OffsetDateTime getDataHoraFim() { return dataHoraFim; }
    public String getMotivo() { return motivo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
