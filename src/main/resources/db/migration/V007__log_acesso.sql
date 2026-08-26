-- =============================================================================
-- V007 - Auditoria de acesso a dados de pacientes
-- Descrição: Cria a tabela sgsm.log_acesso, que registra quem consultou,
--            criou, atualizou, inativou, reativou, exportou ou anonimizou
--            um registro de paciente (item 1 do plano de compliance).
-- =============================================================================

CREATE TABLE IF NOT EXISTS sgsm.log_acesso (
    id           UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   UUID,
    perfil       VARCHAR(30),
    email        VARCHAR(255),
    entidade     VARCHAR(50)              NOT NULL,
    entidade_id  UUID                     NOT NULL,
    acao         VARCHAR(30)              NOT NULL,
    criado_em    TIMESTAMPTZ              NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_log_acesso_entidade ON sgsm.log_acesso (entidade, entidade_id);
CREATE INDEX IF NOT EXISTS idx_log_acesso_usuario ON sgsm.log_acesso (usuario_id);
