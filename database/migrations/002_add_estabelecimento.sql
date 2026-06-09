-- ============================================================
-- Migration 002: Adiciona tabela estabelecimento e associativa medico_estabelecimento
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS estabelecimento (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(200)    NOT NULL,
    cnpj            VARCHAR(18)     NOT NULL UNIQUE,
    telefone        VARCHAR(20),
    email           VARCHAR(255),
    logradouro      VARCHAR(200)    NOT NULL,
    numero          VARCHAR(10)     NOT NULL,
    complemento     VARCHAR(100),
    bairro          VARCHAR(100)    NOT NULL,
    cidade          VARCHAR(100)    NOT NULL,
    uf              CHAR(2)         NOT NULL,
    cep             VARCHAR(9)      NOT NULL,
    ativo           BOOLEAN         NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS medico_estabelecimento (
    medico_id           UUID        NOT NULL REFERENCES medico(id),
    estabelecimento_id  UUID        NOT NULL REFERENCES estabelecimento(id),
    ativo               BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (medico_id, estabelecimento_id)
);

CREATE INDEX IF NOT EXISTS idx_estabelecimento_ativo   ON estabelecimento(ativo);
CREATE INDEX IF NOT EXISTS idx_estabelecimento_cidade  ON estabelecimento(cidade);
CREATE INDEX IF NOT EXISTS idx_estabelecimento_uf      ON estabelecimento(uf);

CREATE INDEX IF NOT EXISTS idx_med_est_medico          ON medico_estabelecimento(medico_id);
CREATE INDEX IF NOT EXISTS idx_med_est_estabelecimento ON medico_estabelecimento(estabelecimento_id);

CREATE TRIGGER trg_estabelecimento_ts
    BEFORE UPDATE ON estabelecimento
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

COMMIT;
