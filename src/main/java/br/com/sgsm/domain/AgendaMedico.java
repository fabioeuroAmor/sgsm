package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agenda_medico")
public class AgendaMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "estabelecimento_id")
    private UUID estabelecimentoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DiaSemana diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(name = "duracao_slot_minutos", nullable = false)
    private Integer duracaoSlotMinutos;

    @Column(name = "data_vigencia_inicio", nullable = false)
    private LocalDate dataVigenciaInicio;

    @Column(name = "data_vigencia_fim")
    private LocalDate dataVigenciaFim;

    @Column(nullable = false)
    private Boolean domiciliar;

    @Column(name = "intervalo_deslocamento_minutos")
    private Integer intervaloDeslocamentoMinutos;

    @Column(name = "raio_km", precision = 5, scale = 2)
    private java.math.BigDecimal raioKm;

    @Column(name = "cidade_atendimento", length = 100)
    private String cidadeAtendimento;

    @Column(name = "uf_atendimento", length = 2)
    private String ufAtendimento;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public AgendaMedico() {}

    @PrePersist
    void prePersist() {
        if (this.domiciliar == null) this.domiciliar = false;
        this.ativo = true;
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getMedicoId() { return medicoId; }
    public UUID getEstabelecimentoId() { return estabelecimentoId; }
    public DiaSemana getDiaSemana() { return diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public Integer getDuracaoSlotMinutos() { return duracaoSlotMinutos; }
    public LocalDate getDataVigenciaInicio() { return dataVigenciaInicio; }
    public LocalDate getDataVigenciaFim() { return dataVigenciaFim; }
    public Boolean getDomiciliar() { return domiciliar; }
    public Integer getIntervaloDeslocamentoMinutos() { return intervaloDeslocamentoMinutos; }
    public java.math.BigDecimal getRaioKm() { return raioKm; }
    public String getCidadeAtendimento() { return cidadeAtendimento; }
    public String getUfAtendimento() { return ufAtendimento; }
    public Boolean getAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }

    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }
    public void setEstabelecimentoId(UUID estabelecimentoId) { this.estabelecimentoId = estabelecimentoId; }
    public void setDiaSemana(DiaSemana diaSemana) { this.diaSemana = diaSemana; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public void setHoraFim(LocalTime horaFim) { this.horaFim = horaFim; }
    public void setDuracaoSlotMinutos(Integer duracaoSlotMinutos) { this.duracaoSlotMinutos = duracaoSlotMinutos; }
    public void setDataVigenciaInicio(LocalDate dataVigenciaInicio) { this.dataVigenciaInicio = dataVigenciaInicio; }
    public void setDataVigenciaFim(LocalDate dataVigenciaFim) { this.dataVigenciaFim = dataVigenciaFim; }
    public void setDomiciliar(Boolean domiciliar) { this.domiciliar = domiciliar; }
    public void setIntervaloDeslocamentoMinutos(Integer intervaloDeslocamentoMinutos) { this.intervaloDeslocamentoMinutos = intervaloDeslocamentoMinutos; }
    public void setRaioKm(java.math.BigDecimal raioKm) { this.raioKm = raioKm; }
    public void setCidadeAtendimento(String cidadeAtendimento) { this.cidadeAtendimento = cidadeAtendimento; }
    public void setUfAtendimento(String ufAtendimento) { this.ufAtendimento = ufAtendimento; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
