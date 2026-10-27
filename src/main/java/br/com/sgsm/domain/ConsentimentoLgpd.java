package br.com.sgsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// Consentimento LGPD por clínica e canal (V016). No WhatsApp nasce com o telefone e ganha o
// paciente quando ele se identifica.
@Entity
@Table(name = "consentimento_lgpd")
public class ConsentimentoLgpd {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id")
    private UUID pacienteId;

    @Column(length = 20)
    private String telefone;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(nullable = false, length = 20)
    private String canal;

    @Column(name = "versao_texto", nullable = false, length = 40)
    private String versaoTexto;

    @Column(name = "aceito_em", nullable = false, updatable = false)
    private OffsetDateTime aceitoEm;

    @Column(name = "revogado_em")
    private OffsetDateTime revogadoEm;

    @PrePersist
    void prePersist() {
        this.aceitoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPacienteId() { return pacienteId; }
    public String getTelefone() { return telefone; }
    public UUID getEstabelecimentoId() { return estabelecimentoId; }
    public String getCanal() { return canal; }
    public String getVersaoTexto() { return versaoTexto; }
    public OffsetDateTime getAceitoEm() { return aceitoEm; }
    public OffsetDateTime getRevogadoEm() { return revogadoEm; }

    public void setPacienteId(UUID pacienteId) { this.pacienteId = pacienteId; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public void setEstabelecimentoId(UUID estabelecimentoId) { this.estabelecimentoId = estabelecimentoId; }
    public void setCanal(String canal) { this.canal = canal; }
    public void setVersaoTexto(String versaoTexto) { this.versaoTexto = versaoTexto; }
    public void setRevogadoEm(OffsetDateTime revogadoEm) { this.revogadoEm = revogadoEm; }
}
