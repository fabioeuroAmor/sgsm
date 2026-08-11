-- ============================================================
-- Rollback Migration 005: Remove telefone das views de autenticacao
-- ============================================================

BEGIN;

-- CREATE OR REPLACE VIEW nao remove colunas -- precisa DROP + CREATE, na ordem de
-- dependencia (v_entidades_possiveis depende das outras tres).
DROP VIEW IF EXISTS auth.v_entidades_possiveis;
DROP VIEW IF EXISTS auth.v_medico;
DROP VIEW IF EXISTS auth.v_paciente;
DROP VIEW IF EXISTS auth.v_funcionario;

CREATE VIEW auth.v_medico AS
SELECT id AS referencia_id, email, nome, ativo
FROM sgsm.medico;

CREATE VIEW auth.v_paciente AS
SELECT id AS referencia_id, email, nome, ativo
FROM sgsm.paciente;

CREATE VIEW auth.v_funcionario AS
SELECT id AS referencia_id, email, nome, ativo
FROM sgsm.funcionario;

CREATE VIEW auth.v_entidades_possiveis AS
SELECT referencia_id, email, nome, ativo, 'MEDICO'::text      AS tipo FROM auth.v_medico
UNION ALL
SELECT referencia_id, email, nome, ativo, 'PACIENTE'::text    AS tipo FROM auth.v_paciente
UNION ALL
SELECT referencia_id, email, nome, ativo, 'FUNCIONARIO'::text AS tipo FROM auth.v_funcionario;

COMMIT;
