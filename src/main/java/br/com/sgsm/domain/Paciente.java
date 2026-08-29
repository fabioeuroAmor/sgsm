package br.com.sgsm.domain;

import br.com.sgsm.security.CpfCryptoConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "paciente")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    // Cifrado em repouso (AES-256-GCM, ver CpfCryptoConverter) — não-determinístico,
    // por isso a unicidade não é mais garantida nesta coluna, e sim em cpfHash.
    @Convert(converter = CpfCryptoConverter.class)
    @Column(nullable = false, length = 255)
    private String cpf;

    // Índice cego (HMAC-SHA256 do CPF normalizado) — permite checar duplicidade
    // sem decifrar. Nullable até o backfill (CpfBackfillRunner) migrar linhas antigas.
    @Column(name = "cpf_hash", length = 64)
    private String cpfHash;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(length = 255)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(length = 9)
    private String cep;

    @Column(nullable = false)
    private Boolean ativo;

    // LGPD (item 3 do compliance): timestamp do consentimento explícito no cadastro.
    // Nulo em cadastros anteriores a esta feature — consentimento não é retroativo.
    @Column(name = "consentimento_lgpd_em")
    private OffsetDateTime consentimentoLgpdEm;

    // Distingue "inativo recuperável" (ativo=false, dado intacto) de "anonimizado
    // irreversível" (dados pessoais zerados) — reativar() bloqueia quando true.
    @Column(nullable = false)
    private Boolean anonimizado = false;

    // Data em que o cadastro foi inativado (setado por remover()) — base de contagem
    // da retenção de 20 anos da POLITICA_RETENCAO_DADOS.md. Nulo enquanto ativo.
    @Column(name = "encerrado_em")
    private OffsetDateTime encerradoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public Paciente() {}

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
    public String getCpf() { return cpf; }
    public String getCpfHash() { return cpfHash; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public String getEmail() { return email; }
    public String getTelefone() { return telefone; }
    public String getLogradouro() { return logradouro; }
    public String getNumero() { return numero; }
    public String getComplemento() { return complemento; }
    public String getBairro() { return bairro; }
    public String getCidade() { return cidade; }
    public String getUf() { return uf; }
    public String getCep() { return cep; }
    public Boolean getAtivo() { return ativo; }
    public OffsetDateTime getConsentimentoLgpdEm() { return consentimentoLgpdEm; }
    public Boolean getAnonimizado() { return anonimizado; }
    public OffsetDateTime getEncerradoEm() { return encerradoEm; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }

    public void setNome(String nome) { this.nome = nome; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public void setCpfHash(String cpfHash) { this.cpfHash = cpfHash; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }
    public void setEmail(String email) { this.email = email; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }
    public void setNumero(String numero) { this.numero = numero; }
    public void setComplemento(String complemento) { this.complemento = complemento; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public void setUf(String uf) { this.uf = uf; }
    public void setCep(String cep) { this.cep = cep; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public void setConsentimentoLgpdEm(OffsetDateTime consentimentoLgpdEm) { this.consentimentoLgpdEm = consentimentoLgpdEm; }
    public void setAnonimizado(Boolean anonimizado) { this.anonimizado = anonimizado; }
    public void setEncerradoEm(OffsetDateTime encerradoEm) { this.encerradoEm = encerradoEm; }
}
