-- ============================================================
-- Rollback Migration 003: Remove sgsm.funcionario e views associadas
--
-- Restaura auth.v_entidades_possiveis para o estado anterior
-- (FUNCIONARIO mapeado por auth.v_estabelecimento) antes de
-- remover a view e a tabela novas.
-- ============================================================

BEGIN;

CREATE OR REPLACE VIEW auth.v_entidades_possiveis AS
SELECT referencia_id, email, nome, ativo, 'MEDICO'::text      AS tipo FROM auth.v_medico
UNION ALL
SELECT referencia_id, email, nome, ativo, 'PACIENTE'::text    AS tipo FROM auth.v_paciente
UNION ALL
SELECT referencia_id, email, nome, ativo, 'FUNCIONARIO'::text AS tipo FROM auth.v_estabelecimento;

DROP VIEW IF EXISTS auth.v_funcionario;

DROP TABLE IF EXISTS sgsm.funcionario;

COMMIT;
