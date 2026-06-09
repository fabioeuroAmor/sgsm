package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "servico_medico")
public class ServicoMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "duracao_minutos")
    private Integer duracaoMinutos;

    @Column(nullable = false)
    private Boolean domiciliar;

    @Column(name = "taxa_deslocamento", precision = 10, scale = 2)
    private java.math.BigDecimal taxaDeslocamento;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public ServicoMedico() {}

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
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public BigDecimal getPreco() { return preco; }
    public Integer getDuracaoMinutos() { return duracaoMinutos; }
    public Boolean getDomiciliar() { return domiciliar; }
    public java.math.BigDecimal getTaxaDeslocamento() { return taxaDeslocamento; }
    public Boolean getAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }

    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }
    public void setNome(String nome) { this.nome = nome; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    public void setDuracaoMinutos(Integer duracaoMinutos) { this.duracaoMinutos = duracaoMinutos; }
    public void setDomiciliar(Boolean domiciliar) { this.domiciliar = domiciliar; }
    public void setTaxaDeslocamento(java.math.BigDecimal taxaDeslocamento) { this.taxaDeslocamento = taxaDeslocamento; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
