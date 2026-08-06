package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medico")
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 20)
    private String crm;

    @Column(name = "crm_uf", nullable = false, length = 2)
    private String crmUf;

    @Column(nullable = false, length = 100)
    private String especialidade;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(nullable = false, length = 20, unique = true)
    private String telefone;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public Medico() {}

    @PrePersist
    void prePersist() {
        this.ativo = true;
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getCrm() { return crm; }
    public String getCrmUf() { return crmUf; }
    public String getEspecialidade() { return especialidade; }
    public String getEmail() { return email; }
    public String getTelefone() { return telefone; }
    public Boolean getAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }

    public void setNome(String nome) { this.nome = nome; }
    public void setCrm(String crm) { this.crm = crm; }
    public void setCrmUf(String crmUf) { this.crmUf = crmUf; }
    public void setEspecialidade(String especialidade) { this.especialidade = especialidade; }
    public void setEmail(String email) { this.email = email; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
