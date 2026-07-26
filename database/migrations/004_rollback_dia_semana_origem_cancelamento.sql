-- ============================================================
-- Rollback Migration 004
-- ============================================================

BEGIN;

ALTER TABLE sgsm.agendamento
    DROP COLUMN IF EXISTS origem_cancelamento;

ALTER TABLE sgsm.agenda_medico
    DROP COLUMN IF EXISTS dia_semana;

DROP TYPE IF EXISTS sgsm.origem_cancelamento;
DROP TYPE IF EXISTS sgsm.dia_semana;

COMMIT;
