-- ============================================================
-- Rollback Migration 005
-- ============================================================

BEGIN;

ALTER TABLE sgsm.historico_status_pagamento
    DROP COLUMN IF EXISTS status_novo,
    DROP COLUMN IF EXISTS status_anterior;

ALTER TABLE sgsm.historico_status_agendamento
    DROP COLUMN IF EXISTS status_novo,
    DROP COLUMN IF EXISTS status_anterior;

ALTER TABLE sgsm.bloqueio_agenda
    DROP COLUMN IF EXISTS tipo;

ALTER TABLE sgsm.reembolso
    DROP COLUMN IF EXISTS status;

ALTER TABLE sgsm.pagamento
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS metodo;

ALTER TABLE sgsm.agendamento
    DROP COLUMN IF EXISTS tipo,
    DROP COLUMN IF EXISTS status;

DROP TYPE IF EXISTS sgsm.tipo_bloqueio;
DROP TYPE IF EXISTS sgsm.status_reembolso;
DROP TYPE IF EXISTS sgsm.status_pagamento;
DROP TYPE IF EXISTS sgsm.metodo_pagamento;
DROP TYPE IF EXISTS sgsm.tipo_agendamento;
DROP TYPE IF EXISTS sgsm.status_agendamento;

COMMIT;
