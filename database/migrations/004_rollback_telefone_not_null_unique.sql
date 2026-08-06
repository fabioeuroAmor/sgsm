-- ============================================================
-- Rollback Migration 004: Telefone volta a ser opcional e nao-unico
--
-- Nao reverte os placeholders "SEMTEL..." gravados no backfill da migration
-- 004 — o dado original (telefone nulo) ja foi sobrescrito.
-- ============================================================

BEGIN;

ALTER TABLE sgsm.funcionario
    DROP CONSTRAINT IF EXISTS funcionario_telefone_key,
    ALTER COLUMN telefone DROP NOT NULL;

ALTER TABLE sgsm.medico
    DROP CONSTRAINT IF EXISTS medico_telefone_key,
    ALTER COLUMN telefone DROP NOT NULL;

ALTER TABLE sgsm.paciente
    DROP CONSTRAINT IF EXISTS paciente_telefone_key,
    ALTER COLUMN telefone DROP NOT NULL;

COMMIT;
