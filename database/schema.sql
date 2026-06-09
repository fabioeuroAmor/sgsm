-- ============================================================
-- SGSM - Sistema de Gerenciamento de Serviços Médicos
-- PostgreSQL Schema
-- ============================================================

-- ============================================================
-- ENUMS
-- ============================================================

CREATE TYPE metodo_pagamento AS ENUM (
    'PIX',
    'CARTAO_CREDITO'
);

CREATE TYPE status_pagamento AS ENUM (
    'PENDENTE',
    'PROCESSANDO',
    'APROVADO',
    'RECUSADO',
    'CANCELADO',
    'REEMBOLSADO'
);

CREATE TYPE status_reembolso AS ENUM (
    'SOLICITADO',
    'PROCESSANDO',
    'CONCLUIDO',
    'RECUSADO'
);

CREATE TYPE dia_semana AS ENUM (
    'SEGUNDA',
    'TERCA',
    'QUARTA',
    'QUINTA',
    'SEXTA',
    'SABADO',
    'DOMINGO'
);

CREATE TYPE tipo_bloqueio AS ENUM (
    'FERIAS',
    'FERIADO',
    'INTERVALO',
    'OUTRO'
);

CREATE TYPE status_agendamento AS ENUM (
    'PENDENTE',
    'AGUARDANDO_PAGAMENTO',
    'CONFIRMADO',
    'EM_ANDAMENTO',
    'CONCLUIDO',
    'CANCELADO',
    'NO_SHOW'
);

CREATE TYPE origem_cancelamento AS ENUM (
    'PACIENTE',
    'MEDICO',
    'ESTABELECIMENTO',
    'SISTEMA'
);

-- ============================================================
-- TABELAS PRINCIPAIS
-- ============================================================

-- Médico: 1 médico → N serviços médicos
CREATE TABLE medico (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(150)    NOT NULL,
    crm             VARCHAR(20)     NOT NULL,   -- ex: 123456/SP
    crm_uf          CHAR(2)         NOT NULL,   -- UF do CRM: SP, RJ, MG...
    especialidade   VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    telefone        VARCHAR(20),
    ativo           BOOLEAN         NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (crm, crm_uf)            -- CRM é único por estado
);

-- Estabelecimento: clínica, hospital ou consultório
CREATE TABLE estabelecimento (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(200)    NOT NULL,
    cnpj            VARCHAR(18)     NOT NULL UNIQUE,  -- ex: 12.345.678/0001-99
    telefone        VARCHAR(20),
    email           VARCHAR(255),
    logradouro      VARCHAR(200)    NOT NULL,
    numero          VARCHAR(10)     NOT NULL,
    complemento     VARCHAR(100),
    bairro          VARCHAR(100)    NOT NULL,
    cidade          VARCHAR(100)    NOT NULL,
    uf              CHAR(2)         NOT NULL,
    cep             VARCHAR(9)      NOT NULL,         -- ex: 01310-100
    ativo           BOOLEAN         NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Tabela associativa: médico N <-> N estabelecimento
CREATE TABLE medico_estabelecimento (
    medico_id           UUID        NOT NULL REFERENCES medico(id),
    estabelecimento_id  UUID        NOT NULL REFERENCES estabelecimento(id),
    ativo               BOOLEAN     NOT NULL DEFAULT TRUE,  -- desvincula sem apagar o histórico
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (medico_id, estabelecimento_id)
);

-- UC01, UC02, UC03, UC04, UC05: CRUD de Serviços Médicos
CREATE TABLE servico_medico (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    medico_id       UUID            NOT NULL REFERENCES medico(id),  -- 1 serviço → 1 médico
    nome            VARCHAR(150)    NOT NULL,
    descricao       TEXT,
    preco           NUMERIC(10, 2)  NOT NULL CHECK (preco >= 0),
    duracao_minutos INTEGER         CHECK (duracao_minutos > 0),
    ativo           BOOLEAN         NOT NULL DEFAULT TRUE,  -- UC04: inativar (soft delete)
    criado_em       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Paciente: consumidor dos serviços médicos
CREATE TABLE paciente (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(150) NOT NULL,
    cpf             VARCHAR(14)  NOT NULL UNIQUE,
    data_nascimento DATE         NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    telefone        VARCHAR(20),
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- UC06, UC09: Registro e consulta de pagamentos
CREATE TABLE pagamento (
    id                  UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id         UUID                REFERENCES paciente(id),
    servico_medico_id   UUID                NOT NULL REFERENCES servico_medico(id),
    valor               NUMERIC(10, 2)      NOT NULL CHECK (valor > 0),
    metodo              metodo_pagamento    NOT NULL,
    status              status_pagamento    NOT NULL DEFAULT 'PENDENTE',
    criado_em           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    atualizado_em       TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

-- UC07: Pagamento via Cartão de Crédito
CREATE TABLE pagamento_cartao (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id        UUID        NOT NULL UNIQUE REFERENCES pagamento(id),
    numero_mascarado    VARCHAR(19) NOT NULL,  -- ex: **** **** **** 1234
    bandeira            VARCHAR(50) NOT NULL,  -- ex: VISA, MASTERCARD
    parcelas            SMALLINT    NOT NULL DEFAULT 1 CHECK (parcelas BETWEEN 1 AND 12),
    codigo_autorizacao  VARCHAR(100),
    nsu                 VARCHAR(50),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- UC08: Pagamento via Pix
CREATE TABLE pagamento_pix (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id    UUID        NOT NULL UNIQUE REFERENCES pagamento(id),
    txid            VARCHAR(35) NOT NULL UNIQUE,  -- ID único da transação Pix (Banco Central)
    chave_pix       VARCHAR(150),
    qr_code         TEXT,
    codigo_copia_cola TEXT,
    data_expiracao  TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- UC10: Cancelamento e reembolso
CREATE TABLE reembolso (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id    UUID                NOT NULL REFERENCES pagamento(id),
    motivo          TEXT                NOT NULL,
    valor           NUMERIC(10, 2)      NOT NULL CHECK (valor > 0),
    status          status_reembolso    NOT NULL DEFAULT 'SOLICITADO',
    criado_em       TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

-- Auditoria de mudanças de status do pagamento
CREATE TABLE historico_status_pagamento (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id    UUID                NOT NULL REFERENCES pagamento(id),
    status_anterior status_pagamento,
    status_novo     status_pagamento    NOT NULL,
    observacao      TEXT,
    criado_em       TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

-- Agenda do médico: template de disponibilidade recorrente por local e dia da semana
CREATE TABLE agenda_medico (
    id                   UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    medico_id            UUID       NOT NULL REFERENCES medico(id),
    estabelecimento_id   UUID       NOT NULL REFERENCES estabelecimento(id),
    dia_semana           dia_semana NOT NULL,
    hora_inicio          TIME       NOT NULL,
    hora_fim             TIME       NOT NULL,
    duracao_slot_minutos INTEGER    NOT NULL DEFAULT 30 CHECK (duracao_slot_minutos > 0),
    data_vigencia_inicio DATE       NOT NULL,
    data_vigencia_fim    DATE,                   -- NULL = vigência indefinida
    ativo                BOOLEAN    NOT NULL DEFAULT TRUE,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_agenda_horario CHECK (hora_fim > hora_inicio),
    UNIQUE (medico_id, estabelecimento_id, dia_semana, hora_inicio)
);

-- Bloqueios de agenda: férias, feriados, intervalos e ausências pontuais
CREATE TABLE bloqueio_agenda (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    medico_id            UUID          NOT NULL REFERENCES medico(id),
    estabelecimento_id   UUID          REFERENCES estabelecimento(id),  -- NULL = todos os locais
    tipo                 tipo_bloqueio NOT NULL DEFAULT 'OUTRO',
    data_hora_inicio     TIMESTAMPTZ   NOT NULL,
    data_hora_fim        TIMESTAMPTZ   NOT NULL,
    motivo               VARCHAR(300),
    criado_em            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bloqueio_periodo CHECK (data_hora_fim > data_hora_inicio)
);

-- Agendamento: vínculo entre paciente, serviço, médico, local, horário e pagamento
CREATE TABLE agendamento (
    id                   UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id          UUID                NOT NULL REFERENCES paciente(id),
    servico_medico_id    UUID                NOT NULL REFERENCES servico_medico(id),
    medico_id            UUID                NOT NULL REFERENCES medico(id),
    estabelecimento_id   UUID                NOT NULL REFERENCES estabelecimento(id),
    pagamento_id         UUID                UNIQUE REFERENCES pagamento(id),  -- NULL até o pagamento ser criado
    data_hora_inicio     TIMESTAMPTZ         NOT NULL,
    data_hora_fim        TIMESTAMPTZ         NOT NULL,
    status               status_agendamento  NOT NULL DEFAULT 'PENDENTE',
    observacoes          TEXT,
    origem_cancelamento  origem_cancelamento,
    motivo_cancelamento  VARCHAR(500),
    criado_em            TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    atualizado_em        TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_agendamento_periodo CHECK (data_hora_fim > data_hora_inicio)
);

-- Impede double-booking: um médico não pode ter dois agendamentos ativos se sobrepondo
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE agendamento
    ADD CONSTRAINT excl_sem_conflito_medico
    EXCLUDE USING gist (
        medico_id WITH =,
        tstzrange(data_hora_inicio, data_hora_fim, '[)') WITH &&
    ) WHERE (status NOT IN ('CANCELADO', 'NO_SHOW'));

-- Auditoria de mudanças de status do agendamento
CREATE TABLE historico_status_agendamento (
    id               UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    agendamento_id   UUID                NOT NULL REFERENCES agendamento(id),
    status_anterior  status_agendamento,
    status_novo      status_agendamento  NOT NULL,
    observacao       VARCHAR(500),
    criado_em        TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ÍNDICES
-- ============================================================

CREATE INDEX idx_medico_ativo            ON medico(ativo);
CREATE INDEX idx_medico_especialidade    ON medico(especialidade);

CREATE INDEX idx_estabelecimento_ativo   ON estabelecimento(ativo);
CREATE INDEX idx_estabelecimento_cidade  ON estabelecimento(cidade);
CREATE INDEX idx_estabelecimento_uf      ON estabelecimento(uf);

CREATE INDEX idx_med_est_medico          ON medico_estabelecimento(medico_id);
CREATE INDEX idx_med_est_estabelecimento ON medico_estabelecimento(estabelecimento_id);

CREATE INDEX idx_servico_medico_medico   ON servico_medico(medico_id);
CREATE INDEX idx_servico_medico_ativo    ON servico_medico(ativo);
CREATE INDEX idx_servico_medico_nome     ON servico_medico(nome);

CREATE INDEX idx_pagamento_servico       ON pagamento(servico_medico_id);
CREATE INDEX idx_pagamento_status        ON pagamento(status);
CREATE INDEX idx_pagamento_metodo        ON pagamento(metodo);
CREATE INDEX idx_pagamento_criado_em     ON pagamento(criado_em);

CREATE INDEX idx_pagamento_pix_txid      ON pagamento_pix(txid);

CREATE INDEX idx_reembolso_pagamento     ON reembolso(pagamento_id);
CREATE INDEX idx_reembolso_status        ON reembolso(status);

CREATE INDEX idx_historico_pagamento_id  ON historico_status_pagamento(pagamento_id);

CREATE INDEX idx_paciente_cpf                ON paciente(cpf);
CREATE INDEX idx_paciente_ativo              ON paciente(ativo);

CREATE INDEX idx_agenda_medico_id            ON agenda_medico(medico_id);
CREATE INDEX idx_agenda_estabelecimento_id   ON agenda_medico(estabelecimento_id);
CREATE INDEX idx_agenda_dia_semana           ON agenda_medico(dia_semana);
CREATE INDEX idx_agenda_ativo                ON agenda_medico(ativo);

CREATE INDEX idx_bloqueio_medico_id          ON bloqueio_agenda(medico_id);
CREATE INDEX idx_bloqueio_periodo            ON bloqueio_agenda(data_hora_inicio, data_hora_fim);

CREATE INDEX idx_agendamento_paciente        ON agendamento(paciente_id);
CREATE INDEX idx_agendamento_medico          ON agendamento(medico_id);
CREATE INDEX idx_agendamento_estabelecimento ON agendamento(estabelecimento_id);
CREATE INDEX idx_agendamento_servico         ON agendamento(servico_medico_id);
CREATE INDEX idx_agendamento_status          ON agendamento(status);
CREATE INDEX idx_agendamento_data_inicio     ON agendamento(data_hora_inicio);
CREATE INDEX idx_pagamento_paciente          ON pagamento(paciente_id);

CREATE INDEX idx_hist_agend_id               ON historico_status_agendamento(agendamento_id);

-- ============================================================
-- FUNÇÃO E TRIGGERS: atualizar campo atualizado_em
-- ============================================================

CREATE OR REPLACE FUNCTION fn_atualizar_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.atualizado_em = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_medico_ts
    BEFORE UPDATE ON medico
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

CREATE TRIGGER trg_estabelecimento_ts
    BEFORE UPDATE ON estabelecimento
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

CREATE TRIGGER trg_servico_medico_ts
    BEFORE UPDATE ON servico_medico
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

CREATE TRIGGER trg_pagamento_ts
    BEFORE UPDATE ON pagamento
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

CREATE TRIGGER trg_reembolso_ts
    BEFORE UPDATE ON reembolso
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

CREATE TRIGGER trg_paciente_ts
    BEFORE UPDATE ON paciente
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

CREATE TRIGGER trg_agenda_medico_ts
    BEFORE UPDATE ON agenda_medico
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

CREATE TRIGGER trg_agendamento_ts
    BEFORE UPDATE ON agendamento
    FOR EACH ROW EXECUTE FUNCTION fn_atualizar_timestamp();

-- ============================================================
-- TRIGGER: registrar histórico automaticamente ao mudar status
-- ============================================================

CREATE OR REPLACE FUNCTION fn_registrar_historico_pagamento()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO historico_status_pagamento (pagamento_id, status_anterior, status_novo)
        VALUES (NEW.id, OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_historico_status_pagamento
    AFTER UPDATE ON pagamento
    FOR EACH ROW EXECUTE FUNCTION fn_registrar_historico_pagamento();

-- ============================================================
-- TRIGGER: registrar histórico automaticamente ao mudar status do agendamento
-- ============================================================

CREATE OR REPLACE FUNCTION fn_registrar_historico_agendamento()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO historico_status_agendamento (agendamento_id, status_anterior, status_novo)
        VALUES (NEW.id, OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_historico_status_agendamento
    AFTER UPDATE ON agendamento
    FOR EACH ROW EXECUTE FUNCTION fn_registrar_historico_agendamento();
