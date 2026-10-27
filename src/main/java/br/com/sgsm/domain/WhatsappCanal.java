package br.com.sgsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// Número de WhatsApp de uma clínica (V014). status: PENDENTE_QR, CONECTADO, DESCONECTADO, REMOVIDO
@Entity
@Table(name = "whatsapp_canal")
public class WhatsappCanal {

    public static final String PENDENTE_QR = "PENDENTE_QR";
    public static final String CONECTADO = "CONECTADO";
    public static final String DESCONECTADO = "DESCONECTADO";
    public static final String REMOVIDO = "REMOVIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(nullable = false, length = 20)
    private String provedor = "EVOLUTION";

    @Column(nullable = false, length = 100, unique = true)
    private String instancia;

    @Column(name = "numero_e164", length = 20)
    private String numeroE164;

    @Column(nullable = false, length = 20)
    private String status = PENDENTE_QR;

    @Column(name = "conectado_em")
    private OffsetDateTime conectadoEm;

    @Column(name = "criado_por")
    private UUID criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getEstabelecimentoId() { return estabelecimentoId; }
    public String getProvedor() { return provedor; }
    public String getInstancia() { return instancia; }
    public String getNumeroE164() { return numeroE164; }
    public String getStatus() { return status; }
    public OffsetDateTime getConectadoEm() { return conectadoEm; }
    public UUID getCriadoPor() { return criadoPor; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }

    public void setEstabelecimentoId(UUID estabelecimentoId) { this.estabelecimentoId = estabelecimentoId; }
    public void setProvedor(String provedor) { this.provedor = provedor; }
    public void setInstancia(String instancia) { this.instancia = instancia; }
    public void setNumeroE164(String numeroE164) { this.numeroE164 = numeroE164; }
    public void setStatus(String status) { this.status = status; }
    public void setConectadoEm(OffsetDateTime conectadoEm) { this.conectadoEm = conectadoEm; }
    public void setCriadoPor(UUID criadoPor) { this.criadoPor = criadoPor; }
}
