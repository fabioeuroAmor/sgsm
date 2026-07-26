-- ============================================================
-- Migration 005: Adiciona colunas de status/tipo faltantes
--
-- O banco deste ambiente foi criado com uma versao mais antiga do schema
-- de referencia (database/criacao/004_schema_sgsm.sql) e ficou sem varias
-- colunas de status/tipo que as entidades JPA ja esperam -- causando 500
-- (coluna nao existe) em qualquer operacao de agendamento, pagamento,
-- reembolso, bloqueio de agenda ou historico de status.
--
-- Todas as tabelas abaixo (exceto agendamento, que ja tem 1 linha) estao
-- vazias neste ambiente, entao os defaults usados aqui sao os mesmos do
-- schema de referencia -- nao sao "chutes", refletem exatamente o que
-- 004_schema_sgsm.sql ja define.
-- ============================================================

BEGIN;

DO $$ BEGIN
    CREATE TYPE sgsm.status_agendamento AS ENUM (
        'PENDENTE', 'AGUARDANDO_PAGAMENTO', 'CONFIRMADO', 'EM_ANDAMENTO',
        'A_CAMINHO', 'CHEGOU', 'CONCLUIDO', 'CANCELADO', 'NO_SHOW'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE sgsm.tipo_agendamento AS ENUM ('PRESENCIAL', 'DOMICILIAR', 'TELEMEDICINA');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE sgsm.metodo_pagamento AS ENUM ('PIX', 'CARTAO_CREDITO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE sgsm.status_pagamento AS ENUM ('PENDENTE', 'APROVADO', 'RECUSADO', 'ESTORNADO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE sgsm.status_reembolso AS ENUM ('SOLICITADO', 'APROVADO', 'RECUSADO', 'CONCLUIDO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE sgsm.tipo_bloqueio AS ENUM ('FERIAS', 'FOLGA', 'CONGRESSO', 'OUTRO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

ALTER TABLE sgsm.agendamento
    ADD COLUMN IF NOT EXISTS status sgsm.status_agendamento DEFAULT 'PENDENTE'::sgsm.status_agendamento NOT NULL,
    ADD COLUMN IF NOT EXISTS tipo   sgsm.tipo_agendamento   DEFAULT 'PRESENCIAL'::sgsm.tipo_agendamento NOT NULL;

ALTER TABLE sgsm.pagamento
    ADD COLUMN IF NOT EXISTS metodo sgsm.metodo_pagamento NOT NULL DEFAULT 'PIX',
    ADD COLUMN IF NOT EXISTS status sgsm.status_pagamento DEFAULT 'PENDENTE'::sgsm.status_pagamento NOT NULL;
ALTER TABLE sgsm.pagamento ALTER COLUMN metodo DROP DEFAULT;

ALTER TABLE sgsm.reembolso
    ADD COLUMN IF NOT EXISTS status sgsm.status_reembolso DEFAULT 'SOLICITADO'::sgsm.status_reembolso NOT NULL;

ALTER TABLE sgsm.bloqueio_agenda
    ADD COLUMN IF NOT EXISTS tipo sgsm.tipo_bloqueio DEFAULT 'OUTRO'::sgsm.tipo_bloqueio NOT NULL;

ALTER TABLE sgsm.historico_status_agendamento
    ADD COLUMN IF NOT EXISTS status_anterior sgsm.status_agendamento,
    ADD COLUMN IF NOT EXISTS status_novo     sgsm.status_agendamento NOT NULL DEFAULT 'PENDENTE';
ALTER TABLE sgsm.historico_status_agendamento ALTER COLUMN status_novo DROP DEFAULT;

ALTER TABLE sgsm.historico_status_pagamento
    ADD COLUMN IF NOT EXISTS status_anterior sgsm.status_pagamento,
    ADD COLUMN IF NOT EXISTS status_novo     sgsm.status_pagamento NOT NULL DEFAULT 'PENDENTE';
ALTER TABLE sgsm.historico_status_pagamento ALTER COLUMN status_novo DROP DEFAULT;

COMMIT;
