-- ============================================================
-- Migration 001: Adiciona tabela medico e vincula a servico_medico
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS medico (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(150)    NOT NULL,
    crm             VARCHAR(20)     NOT NULL,
    crm_uf          CHAR(2)         NOT NULL,
    especialidade   VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    telefone        VARCHAR(20),
    ativo           BOOLEAN         NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (crm, crm_uf)
);

CREATE INDEX IF NOT EXISTS idx_medico_ativo         ON medico(ativo);
CREATE INDEX IF NOT EXISTS idx_medico_especialidade ON medico(especialidade);

-- Adiciona coluna medico_id em servico_medico
ALTER TABLE servico_medico
    ADD COLUMN IF NOT EXISTS medico_id UUID REFERENCES medico(id);

-- ATENÇÃO: preencha medico_id nos registros existentes antes de aplicar NOT NULL
-- UPDATE servico_medico SET medico_id = '<uuid-do-medico>' WHERE medico_id IS NULL;

-- Após preencher todos os registros, aplique a restrição NOT NULL:
-- ALTER TABLE servico_medico ALTER COLUMN medico_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_servico_medico_medico ON servico_medico(medico_id);

-- Trigger de timestamp para medico
CREATE TRIGGER trg_medico_ts
    BEFORE UPDATE ON medico
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

COMMIT;
