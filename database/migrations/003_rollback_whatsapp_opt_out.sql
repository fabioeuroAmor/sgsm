-- ============================================================
-- Rollback Migration 003: Remove opt-out de notificacoes proativas via WhatsApp
-- ============================================================

BEGIN;

ALTER TABLE sgsm.paciente
    DROP COLUMN IF EXISTS whatsapp_opt_out;

COMMIT;
