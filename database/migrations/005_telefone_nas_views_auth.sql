-- ============================================================
-- Migration 005: Telefone exposto nas views de autenticacao
--
-- Fase 2 do plano RAG-First para o canal WhatsApp (PLANO_WHATSAPP_RAG_SGSM.docx
-- v2.0): login sem senha via OTP precisa que o ms-sboot-auth ache o auth.usuario
-- correspondente a um numero de telefone. As views auth.v_medico/v_paciente/
-- v_funcionario (e a auth.v_entidades_possiveis, que as une) so expunham
-- referencia_id/email/nome/ativo -- sem telefone, mesmo ele ja sendo NOT NULL
-- UNIQUE em sgsm.paciente/medico/funcionario desde a migration 004.
--
-- Aditivo: so acrescenta a coluna telefone as views, nao remove nem renomeia
-- nada que ja existe. auth.v_estabelecimento fica de fora (nao e um perfil que
-- autentica por telefone no canal WhatsApp).
-- ============================================================

BEGIN;

-- CREATE OR REPLACE VIEW so aceita colunas novas no final da lista (nao no meio) --
-- telefone entra depois de ativo para nao quebrar a posicao das colunas existentes.
CREATE OR REPLACE VIEW auth.v_medico AS
SELECT id AS referencia_id, email, nome, ativo, telefone
FROM sgsm.medico;

CREATE OR REPLACE VIEW auth.v_paciente AS
SELECT id AS referencia_id, email, nome, ativo, telefone
FROM sgsm.paciente;

CREATE OR REPLACE VIEW auth.v_funcionario AS
SELECT id AS referencia_id, email, nome, ativo, telefone
FROM sgsm.funcionario;

CREATE OR REPLACE VIEW auth.v_entidades_possiveis AS
SELECT referencia_id, email, nome, ativo, 'MEDICO'::text      AS tipo, telefone FROM auth.v_medico
UNION ALL
SELECT referencia_id, email, nome, ativo, 'PACIENTE'::text    AS tipo, telefone FROM auth.v_paciente
UNION ALL
SELECT referencia_id, email, nome, ativo, 'FUNCIONARIO'::text AS tipo, telefone FROM auth.v_funcionario;
-- ^ telefone entra apos "tipo" (ultima coluna da view original) pelo mesmo motivo acima.

COMMIT;
