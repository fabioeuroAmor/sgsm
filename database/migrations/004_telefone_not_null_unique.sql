-- ============================================================
-- Migration 004: Telefone passa a ser obrigatorio e unico
--
-- Fase 0 do plano RAG-First para o canal WhatsApp (PLANO_WHATSAPP_RAG_SGSM.docx
-- v2.0): telefone unico e a chave de identificacao de paciente/medico/funcionario
-- pelo bot. Registros existentes sem telefone (dados de desenvolvimento) recebem
-- um placeholder distinto antes da constraint ser aplicada.
-- ============================================================

BEGIN;

UPDATE sgsm.paciente
SET telefone = 'SEMTEL' || substr(replace(id::text, '-', ''), 1, 14)
WHERE telefone IS NULL;

UPDATE sgsm.medico
SET telefone = 'SEMTEL' || substr(replace(id::text, '-', ''), 1, 14)
WHERE telefone IS NULL;

UPDATE sgsm.funcionario
SET telefone = 'SEMTEL' || substr(replace(id::text, '-', ''), 1, 14)
WHERE telefone IS NULL;

ALTER TABLE sgsm.paciente
    ALTER COLUMN telefone SET NOT NULL,
    ADD CONSTRAINT paciente_telefone_key UNIQUE (telefone);

ALTER TABLE sgsm.medico
    ALTER COLUMN telefone SET NOT NULL,
    ADD CONSTRAINT medico_telefone_key UNIQUE (telefone);

ALTER TABLE sgsm.funcionario
    ALTER COLUMN telefone SET NOT NULL,
    ADD CONSTRAINT funcionario_telefone_key UNIQUE (telefone);

COMMIT;
