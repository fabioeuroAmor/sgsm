-- =============================================================================
-- V012 - Tráfego pago (Meta Ads) por estabelecimento
-- Descrição: schema marketing para a integração com a Marketing API da Meta
--            (Plano_Desenvolvimento_Trafego_Pago_Meta_Ads_SGSM.docx, Etapa 1).
--            O ADMIN_ESTABELECIMENTO conecta a conta Meta da clínica via OAuth;
--            o sgsm-ia sincroniza campanhas e métricas diárias e o dashboard lê
--            daqui (nunca da Meta em tempo real). Toda tabela carrega
--            estabelecimento_id para o filtro por clínica.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS marketing;

-- -----------------------------------------------------------------------------
-- 1. Conexão OAuth — uma por clínica (DM5). Reconectar reaproveita a linha.
--    token_cifrado: AES-256-GCM (chave META_TOKEN_CRYPTO_KEY), nunca em texto puro.
--    Ao desconectar o token é apagado e as métricas ficam (DM6).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marketing.meta_conexao (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID         NOT NULL UNIQUE REFERENCES sgsm.estabelecimento(id),
    meta_user_id       VARCHAR(64),
    meta_user_nome     VARCHAR(200),
    tipo_token         VARCHAR(30)  NOT NULL,   -- USUARIO_LONGA_DURACAO | USUARIO_SISTEMA
    token_cifrado      TEXT,
    escopos            VARCHAR(500),
    expira_em          TIMESTAMPTZ,              -- NULL = não expira (token de usuário do sistema)
    status             VARCHAR(20)  NOT NULL,   -- ATIVA | EXPIRADA | REVOGADA | ERRO
    conectado_por      UUID         REFERENCES auth.usuario(id),
    ultimo_sync_em     TIMESTAMPTZ,
    ultimo_erro        VARCHAR(500),
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_meta_conexao_status CHECK (status IN ('ATIVA', 'EXPIRADA', 'REVOGADA', 'ERRO')),
    CONSTRAINT chk_meta_conexao_tipo CHECK (tipo_token IN ('USUARIO_LONGA_DURACAO', 'USUARIO_SISTEMA'))
);

CREATE INDEX IF NOT EXISTS idx_meta_conexao_meta_user ON marketing.meta_conexao(meta_user_id);

-- -----------------------------------------------------------------------------
-- 2. Contas de anúncio vistas pela conexão; o admin marca quais são da clínica.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marketing.meta_conta_anuncio (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conexao_id         UUID         NOT NULL REFERENCES marketing.meta_conexao(id) ON DELETE CASCADE,
    estabelecimento_id UUID         NOT NULL REFERENCES sgsm.estabelecimento(id),
    act_id             VARCHAR(40)  NOT NULL,   -- formato "act_123"
    nome               VARCHAR(255),
    moeda              VARCHAR(3),
    fuso_horario       VARCHAR(64),
    status_conta       INTEGER,
    selecionada        BOOLEAN      NOT NULL DEFAULT FALSE,
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_meta_conta_estab UNIQUE (estabelecimento_id, act_id)
);

-- -----------------------------------------------------------------------------
-- 3. Hierarquia campanha > conjunto > anúncio (orçamentos em unidades mínimas da
--    moeda, como a Meta devolve — ex.: centavos).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marketing.meta_campanha (
    estabelecimento_id    UUID         NOT NULL REFERENCES sgsm.estabelecimento(id),
    meta_id               VARCHAR(40)  NOT NULL,
    act_id                VARCHAR(40)  NOT NULL,
    nome                  VARCHAR(400),
    status                VARCHAR(30),
    status_efetivo        VARCHAR(40),
    objetivo              VARCHAR(60),
    orcamento_diario      BIGINT,
    orcamento_total       BIGINT,
    inicio                TIMESTAMPTZ,
    fim                   TIMESTAMPTZ,
    atualizado_em         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (estabelecimento_id, meta_id)
);

CREATE TABLE IF NOT EXISTS marketing.meta_conjunto (
    estabelecimento_id    UUID         NOT NULL REFERENCES sgsm.estabelecimento(id),
    meta_id               VARCHAR(40)  NOT NULL,
    act_id                VARCHAR(40)  NOT NULL,
    campanha_meta_id      VARCHAR(40),
    nome                  VARCHAR(400),
    status                VARCHAR(30),
    status_efetivo        VARCHAR(40),
    orcamento_diario      BIGINT,
    orcamento_total       BIGINT,
    atualizado_em         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (estabelecimento_id, meta_id)
);

CREATE TABLE IF NOT EXISTS marketing.meta_anuncio (
    estabelecimento_id    UUID         NOT NULL REFERENCES sgsm.estabelecimento(id),
    meta_id               VARCHAR(40)  NOT NULL,
    act_id                VARCHAR(40)  NOT NULL,
    campanha_meta_id      VARCHAR(40),
    conjunto_meta_id      VARCHAR(40),
    nome                  VARCHAR(400),
    status                VARCHAR(30),
    status_efetivo        VARCHAR(40),
    atualizado_em         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (estabelecimento_id, meta_id)
);

-- -----------------------------------------------------------------------------
-- 4. Métricas diárias já normalizadas (leads extraídos de actions[], CTR/CPC/CPM/
--    CPL calculados). Reprocessadas na janela de atribuição; gravação idempotente.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marketing.meta_insight_diario (
    estabelecimento_id    UUID           NOT NULL REFERENCES sgsm.estabelecimento(id),
    nivel                 VARCHAR(12)    NOT NULL,   -- campaign | adset | ad
    objeto_meta_id        VARCHAR(40)    NOT NULL,
    data                  DATE           NOT NULL,
    act_id                VARCHAR(40)    NOT NULL,
    campanha_meta_id      VARCHAR(40),
    impressoes            BIGINT         NOT NULL DEFAULT 0,
    alcance               BIGINT         NOT NULL DEFAULT 0,
    cliques               BIGINT         NOT NULL DEFAULT 0,
    gasto                 NUMERIC(14,2)  NOT NULL DEFAULT 0,
    leads                 INTEGER        NOT NULL DEFAULT 0,
    ctr                   NUMERIC(10,4),
    cpc                   NUMERIC(14,4),
    cpm                   NUMERIC(14,4),
    cpl                   NUMERIC(14,4),
    moeda                 VARCHAR(3),
    atualizado_em         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (estabelecimento_id, nivel, objeto_meta_id, data),
    CONSTRAINT chk_meta_insight_nivel CHECK (nivel IN ('campaign', 'adset', 'ad'))
);

CREATE INDEX IF NOT EXISTS idx_meta_insight_estab_data ON marketing.meta_insight_diario(estabelecimento_id, nivel, data);

-- -----------------------------------------------------------------------------
-- 5. Histórico de sincronizações (diagnóstico)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marketing.meta_sync_execucao (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID         NOT NULL REFERENCES sgsm.estabelecimento(id),
    inicio             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fim                TIMESTAMPTZ,
    status             VARCHAR(20)  NOT NULL,   -- EM_ANDAMENTO | SUCESSO | PARCIAL | ERRO
    campanhas          INTEGER      NOT NULL DEFAULT 0,
    conjuntos          INTEGER      NOT NULL DEFAULT 0,
    anuncios           INTEGER      NOT NULL DEFAULT 0,
    insights           INTEGER      NOT NULL DEFAULT 0,
    erro               VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_meta_sync_estab ON marketing.meta_sync_execucao(estabelecimento_id, inicio DESC);
