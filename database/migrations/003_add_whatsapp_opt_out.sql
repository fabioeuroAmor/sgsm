-- ============================================================
-- Migration 003: Adiciona opt-out de notificacoes proativas via WhatsApp
--
-- Paciente que pedir para parar de receber mensagens proativas (confirmacao
-- de agendamento, lembrete de consulta, recuperacao de inativos) nao pode
-- mais receber nenhuma delas ate reverter o opt-out manualmente.
-- ============================================================

BEGIN;

ALTER TABLE sgsm.paciente
    ADD COLUMN IF NOT EXISTS whatsapp_opt_out BOOLEAN NOT NULL DEFAULT FALSE;

COMMIT;
