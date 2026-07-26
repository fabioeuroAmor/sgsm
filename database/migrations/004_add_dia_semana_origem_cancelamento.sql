-- ============================================================
-- Migration 004: Adiciona colunas dia_semana e origem_cancelamento
--
-- As entidades JPA (AgendaMedico.diaSemana, Agendamento.origemCancelamento)
-- e o schema de referencia (database/criacao/004_schema_sgsm.sql) ja
-- esperavam essas colunas, mas elas nunca foram criadas neste banco --
-- causando erro 500 (coluna nao existe) em qualquer SELECT de agendamento
-- ou agenda_medico.
--
-- ATENCAO: dia_semana e NOT NULL na entidade. As linhas ja existentes em
-- sgsm.agenda_medico recebem o default 'SEGUNDA' abaixo, que provavelmente
-- NAO reflete o dia real da agenda de cada medico -- revise/corrija
-- manualmente se o dia da semana correto importar para os seus testes.
-- ============================================================

BEGIN;

DO $$ BEGIN
    CREATE TYPE sgsm.dia_semana AS ENUM (
        'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO', 'DOMINGO'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE sgsm.origem_cancelamento AS ENUM (
        'PACIENTE', 'MEDICO', 'ESTABELECIMENTO', 'SISTEMA'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE sgsm.agenda_medico
    ADD COLUMN IF NOT EXISTS dia_semana sgsm.dia_semana NOT NULL DEFAULT 'SEGUNDA';

ALTER TABLE sgsm.agenda_medico
    ALTER COLUMN dia_semana DROP DEFAULT;

ALTER TABLE sgsm.agendamento
    ADD COLUMN IF NOT EXISTS origem_cancelamento sgsm.origem_cancelamento;

COMMIT;
