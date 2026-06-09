package br.com.sgsm.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "estabelecimento")
public class Estabelecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 18, unique = true)
    private String cnpj;

    @Column(length = 20)
    private String telefone;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 200)
    private String logradouro;

    @Column(nullable = false, length = 10)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(nullable = false, length = 100)
    private String bairro;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(nullable = false, length = 9)
    private String cep;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public Estabelecimento() {}

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
    public String getCnpj() { return cnpj; }
    public String getTelefone() { return telefone; }
    public String getEmail() { return email; }
    public String getLogradouro() { return logradouro; }
    public String getNumero() { return numero; }
    public String getComplemento() { return complemento; }
    public String getBairro() { return bairro; }
    public String getCidade() { return cidade; }
    public String getUf() { return uf; }
    public String getCep() { return cep; }
    public Boolean getAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }

    public void setNome(String nome) { this.nome = nome; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public void setEmail(String email) { this.email = email; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }
    public void setNumero(String numero) { this.numero = numero; }
    public void setComplemento(String complemento) { this.complemento = complemento; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public void setUf(String uf) { this.uf = uf; }
    public void setCep(String cep) { this.cep = cep; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
