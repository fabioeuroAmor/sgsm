-- =============================================================================
-- SGSM - Sistema de Gerenciamento de Serviços Médicos
-- Script 002 - Criação das Tabelas
-- Pré-requisito: 001_tipos.sql já executado
-- Ordem de criação respeita dependências de FK
-- =============================================================================


-- -----------------------------------------------------------------------------
-- MÉDICO
-- -----------------------------------------------------------------------------
CREATE TABLE public.medico (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    nome             VARCHAR(150)  NOT NULL,
    crm              VARCHAR(20)   NOT NULL,
    crm_uf           CHAR(2)       NOT NULL,
    especialidade    VARCHAR(100)  NOT NULL,
    email            VARCHAR(255)  NOT NULL UNIQUE,
    telefone         VARCHAR(20),
    ativo            BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    atualizado_em    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT medico_crm_crm_uf_key UNIQUE (crm, crm_uf)
);


-- -----------------------------------------------------------------------------
-- PACIENTE
-- -----------------------------------------------------------------------------
CREATE TABLE public.paciente (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    nome             VARCHAR(150)  NOT NULL,
    cpf              VARCHAR(14)   NOT NULL UNIQUE,
    data_nascimento  DATE          NOT NULL,
    email            VARCHAR(255)  NOT NULL UNIQUE,
    telefone         VARCHAR(20),
    ativo            BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    atualizado_em    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    -- Endereço (preenchido via ViaCEP)
    logradouro       VARCHAR(255),
    numero           VARCHAR(20),
    complemento      VARCHAR(100),
    bairro           VARCHAR(100),
    cidade           VARCHAR(100),
    uf               CHAR(2),
    cep              VARCHAR(9)
);


-- -----------------------------------------------------------------------------
-- ESTABELECIMENTO
-- -----------------------------------------------------------------------------
CREATE TABLE public.estabelecimento (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    nome             VARCHAR(200)  NOT NULL,
    cnpj             VARCHAR(18)   NOT NULL UNIQUE,
    telefone         VARCHAR(20),
    email            VARCHAR(255),
    logradouro       VARCHAR(200)  NOT NULL,
    numero           VARCHAR(10)   NOT NULL,
    complemento      VARCHAR(100),
    bairro           VARCHAR(100)  NOT NULL,
    cidade           VARCHAR(100)  NOT NULL,
    uf               CHAR(2)       NOT NULL,
    cep              VARCHAR(9)    NOT NULL,
    ativo            BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    atualizado_em    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);


-- -----------------------------------------------------------------------------
-- SERVIÇO MÉDICO
-- -----------------------------------------------------------------------------
CREATE TABLE public.servico_medico (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    medico_id            UUID           NOT NULL REFERENCES public.medico(id),
    nome                 VARCHAR(150)   NOT NULL,
    descricao            TEXT,
    preco                NUMERIC(10,2)  NOT NULL,
    duracao_minutos      INTEGER,
    ativo                BOOLEAN        NOT NULL DEFAULT TRUE,
    criado_em            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    atualizado_em        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    domiciliar           BOOLEAN        NOT NULL DEFAULT FALSE,
    taxa_deslocamento    NUMERIC(10,2),

    CONSTRAINT servico_medico_preco_check          CHECK (preco >= 0),
    CONSTRAINT servico_medico_duracao_minutos_check CHECK (duracao_minutos > 0)
);


-- -----------------------------------------------------------------------------
-- MÉDICO × ESTABELECIMENTO  (N:N)
-- -----------------------------------------------------------------------------
CREATE TABLE public.medico_estabelecimento (
    medico_id            UUID        NOT NULL REFERENCES public.medico(id),
    estabelecimento_id   UUID        NOT NULL REFERENCES public.estabelecimento(id),
    ativo                BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (medico_id, estabelecimento_id)
);


-- -----------------------------------------------------------------------------
-- AGENDA DO MÉDICO
-- -----------------------------------------------------------------------------
CREATE TABLE public.agenda_medico (
    id                              UUID                 PRIMARY KEY DEFAULT gen_random_uuid(),
    medico_id                       UUID                 NOT NULL REFERENCES public.medico(id),
    estabelecimento_id              UUID                 REFERENCES public.estabelecimento(id),
    dia_semana                      public.dia_semana    NOT NULL,
    hora_inicio                     TIME                 NOT NULL,
    hora_fim                        TIME                 NOT NULL,
    duracao_slot_minutos            INTEGER              NOT NULL DEFAULT 30,
    data_vigencia_inicio            DATE                 NOT NULL,
    data_vigencia_fim               DATE,
    ativo                           BOOLEAN              NOT NULL DEFAULT TRUE,
    criado_em                       TIMESTAMPTZ          NOT NULL DEFAULT NOW(),
    atualizado_em                   TIMESTAMPTZ          NOT NULL DEFAULT NOW(),
    -- Atendimento domiciliar
    domiciliar                      BOOLEAN              NOT NULL DEFAULT FALSE,
    raio_km                         NUMERIC(6,2),
    cidade_atendimento              VARCHAR(100),
    uf_atendimento                  CHAR(2),
    intervalo_deslocamento_minutos  INTEGER,

    CONSTRAINT agenda_medico_medico_id_estabelecimento_id_dia_semana_hora__key
        UNIQUE (medico_id, estabelecimento_id, dia_semana, hora_inicio),
    CONSTRAINT agenda_medico_duracao_slot_minutos_check CHECK (duracao_slot_minutos > 0),
    CONSTRAINT chk_agenda_horario                       CHECK (hora_fim > hora_inicio)
);


-- -----------------------------------------------------------------------------
-- BLOQUEIO DE AGENDA
-- -----------------------------------------------------------------------------
CREATE TABLE public.bloqueio_agenda (
    id                  UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),
    medico_id           UUID                  NOT NULL REFERENCES public.medico(id),
    estabelecimento_id  UUID                  REFERENCES public.estabelecimento(id),
    tipo                public.tipo_bloqueio  NOT NULL DEFAULT 'OUTRO',
    data_hora_inicio    TIMESTAMPTZ           NOT NULL,
    data_hora_fim       TIMESTAMPTZ           NOT NULL,
    motivo              VARCHAR(300),
    criado_em           TIMESTAMPTZ           NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_bloqueio_periodo CHECK (data_hora_fim > data_hora_inicio)
);


-- -----------------------------------------------------------------------------
-- PAGAMENTO
-- (criado antes de agendamento pois agendamento tem FK para pagamento)
-- -----------------------------------------------------------------------------
CREATE TABLE public.pagamento (
    id                  UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
    servico_medico_id   UUID                    NOT NULL REFERENCES public.servico_medico(id),
    paciente_id         UUID                    REFERENCES public.paciente(id),
    valor               NUMERIC(10,2)           NOT NULL,
    metodo              public.metodo_pagamento NOT NULL,
    status              public.status_pagamento NOT NULL DEFAULT 'PENDENTE',
    criado_em           TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    atualizado_em       TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pagamento_valor_check CHECK (valor > 0)
);


-- -----------------------------------------------------------------------------
-- AGENDAMENTO
-- -----------------------------------------------------------------------------
CREATE TABLE public.agendamento (
    id                    UUID                       PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id           UUID                       NOT NULL REFERENCES public.paciente(id),
    servico_medico_id     UUID                       NOT NULL REFERENCES public.servico_medico(id),
    medico_id             UUID                       NOT NULL REFERENCES public.medico(id),
    estabelecimento_id    UUID                       REFERENCES public.estabelecimento(id),
    pagamento_id          UUID                       UNIQUE REFERENCES public.pagamento(id),
    data_hora_inicio      TIMESTAMPTZ                NOT NULL,
    data_hora_fim         TIMESTAMPTZ                NOT NULL,
    status                public.status_agendamento  NOT NULL DEFAULT 'PENDENTE',
    tipo                  public.tipo_agendamento    NOT NULL DEFAULT 'PRESENCIAL',
    observacoes           TEXT,
    origem_cancelamento   public.origem_cancelamento,
    motivo_cancelamento   VARCHAR(500),
    localizacao_medico    TEXT,
    criado_em             TIMESTAMPTZ                NOT NULL DEFAULT NOW(),
    atualizado_em         TIMESTAMPTZ                NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_agendamento_periodo CHECK (data_hora_fim > data_hora_inicio)
);


-- -----------------------------------------------------------------------------
-- HISTÓRICO DE STATUS DO AGENDAMENTO
-- -----------------------------------------------------------------------------
CREATE TABLE public.historico_status_agendamento (
    id               UUID                      PRIMARY KEY DEFAULT gen_random_uuid(),
    agendamento_id   UUID                      NOT NULL REFERENCES public.agendamento(id),
    status_anterior  public.status_agendamento,
    status_novo      public.status_agendamento NOT NULL,
    observacao       VARCHAR(500),
    criado_em        TIMESTAMPTZ               NOT NULL DEFAULT NOW()
);


-- -----------------------------------------------------------------------------
-- PAGAMENTO VIA CARTÃO
-- -----------------------------------------------------------------------------
CREATE TABLE public.pagamento_cartao (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id        UUID          NOT NULL UNIQUE REFERENCES public.pagamento(id),
    numero_mascarado    VARCHAR(19)   NOT NULL,
    bandeira            VARCHAR(50)   NOT NULL,
    parcelas            SMALLINT      NOT NULL DEFAULT 1,
    codigo_autorizacao  VARCHAR(100),
    nsu                 VARCHAR(50),
    criado_em           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pagamento_cartao_parcelas_check CHECK (parcelas >= 1 AND parcelas <= 12)
);


-- -----------------------------------------------------------------------------
-- PAGAMENTO VIA PIX
-- -----------------------------------------------------------------------------
CREATE TABLE public.pagamento_pix (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id      UUID         NOT NULL UNIQUE REFERENCES public.pagamento(id),
    txid              VARCHAR(35)  NOT NULL UNIQUE,
    chave_pix         VARCHAR(150),
    qr_code           TEXT,
    codigo_copia_cola TEXT,
    data_expiracao    TIMESTAMPTZ,
    criado_em         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);


-- -----------------------------------------------------------------------------
-- HISTÓRICO DE STATUS DO PAGAMENTO
-- -----------------------------------------------------------------------------
CREATE TABLE public.historico_status_pagamento (
    id               UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id     UUID                    NOT NULL REFERENCES public.pagamento(id),
    status_anterior  public.status_pagamento,
    status_novo      public.status_pagamento NOT NULL,
    observacao       TEXT,
    criado_em        TIMESTAMPTZ             NOT NULL DEFAULT NOW()
);


-- -----------------------------------------------------------------------------
-- REEMBOLSO
-- -----------------------------------------------------------------------------
CREATE TABLE public.reembolso (
    id            UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
    pagamento_id  UUID                    NOT NULL REFERENCES public.pagamento(id),
    motivo        TEXT                    NOT NULL,
    valor         NUMERIC(10,2)           NOT NULL,
    status        public.status_reembolso NOT NULL DEFAULT 'SOLICITADO',
    criado_em     TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT reembolso_valor_check CHECK (valor > 0)
);
