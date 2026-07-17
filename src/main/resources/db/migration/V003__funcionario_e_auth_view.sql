-- =============================================================================
-- V003 - Funcionalidade: Funcionários
-- Descrição: Cria a tabela sgsm.funcionario e atualiza as views de autenticação
--            para que funcionários possam criar login com role FUNCIONARIO.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Tabela de funcionários
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sgsm.funcionario (
    id                 UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    nome               VARCHAR(200)             NOT NULL,
    cpf                VARCHAR(14)              NOT NULL UNIQUE,
    email              VARCHAR(255)             NOT NULL,
    telefone           VARCHAR(20),
    cargo              VARCHAR(100)             NOT NULL,
    estabelecimento_id UUID                     NOT NULL REFERENCES sgsm.estabelecimento(id),
    ativo              BOOLEAN                  NOT NULL DEFAULT TRUE,
    criado_em          TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    atualizado_em      TIMESTAMPTZ              NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 2. View auth.v_funcionario
--    Expõe sgsm.funcionario para o serviço de autenticação (ms-sboot-auth).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW auth.v_funcionario AS
SELECT
    id    AS referencia_id,
    email,
    nome,
    ativo
FROM sgsm.funcionario;

-- -----------------------------------------------------------------------------
-- 3. Atualiza auth.v_entidades_possiveis
--    FUNCIONARIO agora aponta para sgsm.funcionario (antes apontava para
--    sgsm.estabelecimento, que era um mapeamento incorreto).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW auth.v_entidades_possiveis AS
SELECT referencia_id, email, nome, ativo, 'MEDICO'::text      AS tipo FROM auth.v_medico
UNION ALL
SELECT referencia_id, email, nome, ativo, 'PACIENTE'::text    AS tipo FROM auth.v_paciente
UNION ALL
SELECT referencia_id, email, nome, ativo, 'FUNCIONARIO'::text AS tipo FROM auth.v_funcionario;
