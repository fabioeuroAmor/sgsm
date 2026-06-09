package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agendamento")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "servico_medico_id", nullable = false)
    private UUID servicoMedicoId;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "estabelecimento_id")
    private UUID estabelecimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAgendamento tipo;

    @Column(name = "pagamento_id")
    private UUID pagamentoId;

    @Column(name = "data_hora_inicio", nullable = false)
    private OffsetDateTime dataHoraInicio;

    @Column(name = "data_hora_fim", nullable = false)
    private OffsetDateTime dataHoraFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAgendamento status;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_cancelamento")
    private OrigemCancelamento origemCancelamento;

    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    @Column(name = "localizacao_medico", columnDefinition = "TEXT")
    private String localizacaoMedico;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public Agendamento() {}

    @PrePersist
    void prePersist() {
        this.status = StatusAgendamento.PENDENTE;
        if (this.tipo == null) this.tipo = TipoAgendamento.PRESENCIAL;
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPacienteId() { return pacienteId; }
    public UUID getServicoMedicoId() { return servicoMedicoId; }
    public UUID getMedicoId() { return medicoId; }
    public UUID getEstabelecimentoId() { return estabelecimentoId; }
    public TipoAgendamento getTipo() { return tipo; }
    public UUID getPagamentoId() { return pagamentoId; }
    public OffsetDateTime getDataHoraInicio() { return dataHoraInicio; }
    public OffsetDateTime getDataHoraFim() { return dataHoraFim; }
    public StatusAgendamento getStatus() { return status; }
    public String getObservacoes() { return observacoes; }
    public OrigemCancelamento getOrigemCancelamento() { return origemCancelamento; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public String getLocalizacaoMedico() { return localizacaoMedico; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }

    public void setPacienteId(UUID pacienteId) { this.pacienteId = pacienteId; }
    public void setServicoMedicoId(UUID servicoMedicoId) { this.servicoMedicoId = servicoMedicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }
    public void setEstabelecimentoId(UUID estabelecimentoId) { this.estabelecimentoId = estabelecimentoId; }
    public void setTipo(TipoAgendamento tipo) { this.tipo = tipo; }
    public void setPagamentoId(UUID pagamentoId) { this.pagamentoId = pagamentoId; }
    public void setDataHoraInicio(OffsetDateTime dataHoraInicio) { this.dataHoraInicio = dataHoraInicio; }
    public void setDataHoraFim(OffsetDateTime dataHoraFim) { this.dataHoraFim = dataHoraFim; }
    public void setStatus(StatusAgendamento status) { this.status = status; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public void setOrigemCancelamento(OrigemCancelamento origemCancelamento) { this.origemCancelamento = origemCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }
    public void setLocalizacaoMedico(String localizacaoMedico) { this.localizacaoMedico = localizacaoMedico; }
}
