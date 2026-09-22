-- =============================================================================
-- V010 - Funcionalidade: Administrador de Estabelecimento
-- Descrição: Cria a tabela sgsm.administrador (identidade separada de
--            funcionario) e o vínculo N:N sgsm.administrador_estabelecimento,
--            e atualiza as views de autenticação para que administradores
--            possam criar login com role ADMIN_ESTABELECIMENTO.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Tabela de administradores (identidade própria, não é funcionario)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sgsm.administrador (
    id                 UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    nome               VARCHAR(200)             NOT NULL,
    cpf                VARCHAR(14)              NOT NULL UNIQUE,
    email              VARCHAR(255)             NOT NULL,
    telefone           VARCHAR(20),
    ativo              BOOLEAN                  NOT NULL DEFAULT TRUE,
    criado_em          TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    atualizado_em      TIMESTAMPTZ              NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 2. Vínculo N:N administrador <-> estabelecimento (um administrador pode
--    administrar várias clínicas), mesmo formato de medico_estabelecimento.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sgsm.administrador_estabelecimento (
    administrador_id   UUID        NOT NULL REFERENCES sgsm.administrador(id),
    estabelecimento_id UUID        NOT NULL REFERENCES sgsm.estabelecimento(id),
    ativo               BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (administrador_id, estabelecimento_id)
);

CREATE INDEX idx_admin_est_administrador  ON sgsm.administrador_estabelecimento(administrador_id);
CREATE INDEX idx_admin_est_estabelecimento ON sgsm.administrador_estabelecimento(estabelecimento_id);

-- -----------------------------------------------------------------------------
-- 3. View auth.v_administrador
--    Expõe sgsm.administrador para o serviço de autenticação (ms-sboot-auth).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW auth.v_administrador AS
SELECT
    id    AS referencia_id,
    email,
    nome,
    ativo
FROM sgsm.administrador;

-- -----------------------------------------------------------------------------
-- 4. Atualiza auth.v_entidades_possiveis
--    Adiciona ADMIN_ESTABELECIMENTO ao conjunto de identidades reconhecidas.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW auth.v_entidades_possiveis AS
SELECT referencia_id, email, nome, ativo, 'MEDICO'::text               AS tipo FROM auth.v_medico
UNION ALL
SELECT referencia_id, email, nome, ativo, 'PACIENTE'::text             AS tipo FROM auth.v_paciente
UNION ALL
SELECT referencia_id, email, nome, ativo, 'FUNCIONARIO'::text          AS tipo FROM auth.v_funcionario
UNION ALL
SELECT referencia_id, email, nome, ativo, 'ADMIN_ESTABELECIMENTO'::text AS tipo FROM auth.v_administrador;
