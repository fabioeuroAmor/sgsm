-- ============================================================
-- SGSM — Schema CRM
-- CRM Operacional + Analítico + RAG
--
-- Pré-requisitos:
--   1. schema public (SGSM) já criado e populado
--   2. schema auth já criado
--   3. Milvus rodando em localhost:19530 (vetores ficam lá)
--   4. RabbitMQ rodando em localhost:5672 (eventos assíncronos)
--
-- Este script NÃO altera nenhuma tabela existente.
-- Apenas lê (via FK e views) dos schemas public e auth.
--
-- ARQUITETURA DE VETORES:
--   Os embeddings NÃO ficam no PostgreSQL.
--   O Milvus é o banco vetorial (collection: sgsm_documentos).
--   crm.documento armazena apenas metadados e o texto composto,
--   usando milvus_id como chave de referência cruzada.
-- ============================================================

-- ============================================================
-- SCHEMA
-- ============================================================

CREATE SCHEMA IF NOT EXISTS crm;

-- ============================================================
-- ENUMS DO CRM
-- ============================================================

CREATE TYPE crm.tipo_documento AS ENUM (
    'PACIENTE',
    'MEDICO',
    'SERVICO',
    'AGENDAMENTO',
    'ESTABELECIMENTO'
);

CREATE TYPE crm.tipo_contato AS ENUM (
    'LIGACAO',
    'EMAIL',
    'WHATSAPP',
    'SMS',
    'PRESENCIAL'
);

CREATE TYPE crm.direcao_contato AS ENUM (
    'ENTRADA',   -- paciente entrou em contato
    'SAIDA'      -- equipe entrou em contato com o paciente
);

CREATE TYPE crm.status_lead AS ENUM (
    'NOVO',
    'CONTATADO',
    'QUALIFICADO',
    'CONVERTIDO',   -- virou paciente
    'PERDIDO'
);

CREATE TYPE crm.origem_lead AS ENUM (
    'SITE',
    'INDICACAO',
    'REDES_SOCIAIS',
    'TELEFONE',
    'CAMPANHA',
    'OUTRO'
);

CREATE TYPE crm.tipo_nota AS ENUM (
    'ANAMNESE',
    'EVOLUCAO',
    'OBSERVACAO',
    'RETORNO',
    'OUTRO'
);

CREATE TYPE crm.tipo_tag AS ENUM (
    'MANUAL',
    'AUTOMATICO'
);

CREATE TYPE crm.periodo_kpi AS ENUM (
    'DIARIO',
    'SEMANAL',
    'MENSAL'
);

-- ============================================================
-- ENUM DE INDEXAÇÃO — controle de status no Milvus
-- ============================================================

CREATE TYPE crm.status_indexacao AS ENUM (
    'PENDENTE',   -- texto gerado, aguardando sgsm-ia indexar no Milvus
    'INDEXADO',   -- embedding gerado e salvo no Milvus com sucesso
    'ERRO'        -- falha na indexação — sgsm-ia vai tentar novamente
);

-- ============================================================
-- TABELA 1 — crm.documento
-- Armazena metadados e o texto composto de cada registro.
-- O embedding (vetor) vive no Milvus, não aqui.
-- O sgsm-ia lê o conteudo daqui, gera o embedding via LLM
-- e salva no Milvus usando milvus_id como chave de ligação.
-- ============================================================

CREATE TABLE crm.documento (
    id                  UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo                crm.tipo_documento          NOT NULL,
    referencia_id       UUID                        NOT NULL,   -- PK do registro original
    referencia_tabela   VARCHAR(100)                NOT NULL,   -- ex: public.paciente
    conteudo            TEXT                        NOT NULL,   -- texto composto para embedding
    milvus_id           VARCHAR(36),                            -- ID correspondente no Milvus
    status_indexacao    crm.status_indexacao        NOT NULL DEFAULT 'PENDENTE',
    tentativas          SMALLINT                    NOT NULL DEFAULT 0,
    versao              INTEGER                     NOT NULL DEFAULT 1,
    criado_em           TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    atualizado_em       TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    UNIQUE (tipo, referencia_id)
);

COMMENT ON TABLE crm.documento IS
    'Metadados e texto composto de cada registro indexado no Milvus. '
    'O embedding (vetor) vive na collection sgsm_documentos do Milvus. '
    'milvus_id e a chave de referencia cruzada entre as duas bases.';

COMMENT ON COLUMN crm.documento.conteudo IS
    'Texto em linguagem natural gerado via SQL combinando dados de múltiplas tabelas. '
    'Ex: "Paciente João Silva, 38 anos. 4 consultas concluídas. LTV: R$ 1.240,00." '
    'Fonte para o sgsm-ia gerar o embedding via LLM API.';

COMMENT ON COLUMN crm.documento.milvus_id IS
    'ID do documento na collection sgsm_documentos do Milvus. '
    'Preenchido pelo sgsm-ia após indexação bem-sucedida.';

COMMENT ON COLUMN crm.documento.status_indexacao IS
    'PENDENTE: aguardando sgsm-ia processar. '
    'INDEXADO: embedding gerado e salvo no Milvus. '
    'ERRO: falha na indexação (sgsm-ia reprocessa documentos com ERRO).';

-- ============================================================
-- TABELA 2 — crm.tag_paciente
-- Tags manuais ou automáticas aplicadas a pacientes para
-- segmentação e filtros no CRM Operacional.
-- ============================================================

CREATE TABLE crm.tag_paciente (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id     UUID            NOT NULL REFERENCES public.paciente(id) ON DELETE CASCADE,
    tag             VARCHAR(100)    NOT NULL,
    tipo            crm.tipo_tag    NOT NULL DEFAULT 'MANUAL',
    criado_por      UUID            REFERENCES auth.usuario(id),  -- NULL = sistema
    criado_em       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (paciente_id, tag)
);

COMMENT ON TABLE crm.tag_paciente IS
    'Tags de segmentação aplicadas a pacientes. '
    'Exemplos: VIP, INADIMPLENTE, RISCO_CHURN, ALTO_LTV, RETORNO_FREQUENTE.';

-- ============================================================
-- TABELA 3 — crm.contato_paciente
-- Histórico de interações entre a equipe e o paciente
-- (ligações, e-mails, WhatsApp, atendimentos presenciais).
-- ============================================================

CREATE TABLE crm.contato_paciente (
    id                  UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id         UUID                    NOT NULL REFERENCES public.paciente(id),
    tipo                crm.tipo_contato        NOT NULL,
    direcao             crm.direcao_contato     NOT NULL,
    descricao           TEXT                    NOT NULL,
    duracao_segundos    INTEGER                 CHECK (duracao_segundos > 0),  -- apenas para LIGACAO
    usuario_id          UUID                    REFERENCES auth.usuario(id),   -- quem registrou
    agendamento_id      UUID                    REFERENCES public.agendamento(id),  -- contexto opcional
    criado_em           TIMESTAMPTZ             NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE crm.contato_paciente IS
    'Histórico de contatos entre a equipe e os pacientes. '
    'Permite rastrear ligações, e-mails e mensagens fora do fluxo de agendamento.';

-- ============================================================
-- TABELA 4 — crm.lead
-- Prospects que ainda não se tornaram pacientes.
-- Quando convertido, referencia o paciente criado.
-- ============================================================

CREATE TABLE crm.lead (
    id                      UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                    VARCHAR(150)        NOT NULL,
    email                   VARCHAR(255),
    telefone                VARCHAR(20),
    interesse               TEXT,                               -- qual serviço/especialidade busca
    origem                  crm.origem_lead     NOT NULL DEFAULT 'OUTRO',
    status                  crm.status_lead     NOT NULL DEFAULT 'NOVO',
    convertido_paciente_id  UUID                REFERENCES public.paciente(id),  -- NULL até conversão
    responsavel_id          UUID                REFERENCES auth.usuario(id),
    observacoes             TEXT,
    criado_em               TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    atualizado_em           TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE crm.lead IS
    'Prospects (potenciais pacientes) antes do cadastro formal. '
    'Ao converter, convertido_paciente_id aponta para o registro em public.paciente.';

-- ============================================================
-- TABELA 5 — crm.nota_clinica
-- Anotações estruturadas por atendimento, além do campo
-- observacoes já existente em public.agendamento.
-- Candidatas a embedding no crm.documento (tipo AGENDAMENTO).
-- ============================================================

CREATE TABLE crm.nota_clinica (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    agendamento_id  UUID            NOT NULL REFERENCES public.agendamento(id),
    paciente_id     UUID            NOT NULL REFERENCES public.paciente(id),
    medico_id       UUID            NOT NULL REFERENCES public.medico(id),
    tipo            crm.tipo_nota   NOT NULL DEFAULT 'OBSERVACAO',
    conteudo        TEXT            NOT NULL,
    criado_em       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE crm.nota_clinica IS
    'Notas clínicas estruturadas por atendimento. Complementa o campo '
    'observacoes do agendamento com tipo e rastreabilidade. '
    'Também indexadas no crm.documento para busca semântica via RAG.';

-- ============================================================
-- TABELA 6 — crm.kpi_snapshot
-- Snapshots periódicos de KPIs analíticos pré-calculados
-- pelo job ETL. Evita recalcular em tempo de consulta.
-- ============================================================

CREATE TABLE crm.kpi_snapshot (
    id                  UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    periodo             crm.periodo_kpi     NOT NULL,
    data_referencia     DATE                NOT NULL,
    -- dimensões de agrupamento (nullable: NULL = total geral)
    medico_id           UUID                REFERENCES public.medico(id),
    estabelecimento_id  UUID                REFERENCES public.estabelecimento(id),
    especialidade       VARCHAR(100),
    -- KPIs de agendamento
    total_agendamentos          INTEGER         NOT NULL DEFAULT 0,
    agendamentos_concluidos     INTEGER         NOT NULL DEFAULT 0,
    agendamentos_cancelados     INTEGER         NOT NULL DEFAULT 0,
    agendamentos_no_show        INTEGER         NOT NULL DEFAULT 0,
    taxa_conversao              NUMERIC(5, 2),  -- concluidos / total (%)
    taxa_cancelamento           NUMERIC(5, 2),
    taxa_no_show                NUMERIC(5, 2),
    -- KPIs financeiros
    receita_bruta               NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    receita_pix                 NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    receita_cartao              NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_reembolsos            NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    ticket_medio                NUMERIC(10, 2),
    -- KPIs de pacientes
    novos_pacientes             INTEGER         NOT NULL DEFAULT 0,
    pacientes_retorno            INTEGER         NOT NULL DEFAULT 0,
    -- metadado
    criado_em           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    UNIQUE (periodo, data_referencia, medico_id, estabelecimento_id, especialidade)
);

COMMENT ON TABLE crm.kpi_snapshot IS
    'Snapshots pré-calculados de KPIs por período/dimensão. '
    'Populados pelo ETL. Permitem dashboards rápidos sem recalcular sobre '
    'as tabelas transacionais a cada requisição.';

-- ============================================================
-- TABELA 7 — crm.historico_lead
-- Rastreia mudanças de status dos leads (funil de vendas).
-- ============================================================

CREATE TABLE crm.historico_lead (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id         UUID                NOT NULL REFERENCES crm.lead(id) ON DELETE CASCADE,
    status_anterior crm.status_lead,
    status_novo     crm.status_lead     NOT NULL,
    observacao      VARCHAR(500),
    usuario_id      UUID                REFERENCES auth.usuario(id),
    criado_em       TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE crm.historico_lead IS
    'Auditoria de mudanças de status dos leads. '
    'Permite analisar o funil de conversão: NOVO → CONTATADO → QUALIFICADO → CONVERTIDO.';

-- ============================================================
-- ÍNDICES
-- ============================================================

-- crm.documento
CREATE INDEX idx_crm_doc_tipo           ON crm.documento(tipo);
CREATE INDEX idx_crm_doc_referencia     ON crm.documento(referencia_id);
CREATE INDEX idx_crm_doc_atualizado     ON crm.documento(atualizado_em);
CREATE INDEX idx_crm_doc_status         ON crm.documento(status_indexacao);
CREATE INDEX idx_crm_doc_milvus         ON crm.documento(milvus_id);
-- obs: índice vetorial fica no Milvus (HNSW), não aqui

-- crm.tag_paciente
CREATE INDEX idx_crm_tag_paciente       ON crm.tag_paciente(paciente_id);
CREATE INDEX idx_crm_tag_nome           ON crm.tag_paciente(tag);

-- crm.contato_paciente
CREATE INDEX idx_crm_contato_paciente   ON crm.contato_paciente(paciente_id);
CREATE INDEX idx_crm_contato_criado     ON crm.contato_paciente(criado_em);
CREATE INDEX idx_crm_contato_tipo       ON crm.contato_paciente(tipo);

-- crm.lead
CREATE INDEX idx_crm_lead_status        ON crm.lead(status);
CREATE INDEX idx_crm_lead_origem        ON crm.lead(origem);
CREATE INDEX idx_crm_lead_email         ON crm.lead(email);
CREATE INDEX idx_crm_lead_responsavel   ON crm.lead(responsavel_id);

-- crm.nota_clinica
CREATE INDEX idx_crm_nota_agendamento   ON crm.nota_clinica(agendamento_id);
CREATE INDEX idx_crm_nota_paciente      ON crm.nota_clinica(paciente_id);
CREATE INDEX idx_crm_nota_medico        ON crm.nota_clinica(medico_id);

-- crm.kpi_snapshot
CREATE INDEX idx_crm_kpi_periodo        ON crm.kpi_snapshot(periodo, data_referencia);
CREATE INDEX idx_crm_kpi_medico         ON crm.kpi_snapshot(medico_id);
CREATE INDEX idx_crm_kpi_estabelecimento ON crm.kpi_snapshot(estabelecimento_id);

-- crm.historico_lead
CREATE INDEX idx_crm_hist_lead          ON crm.historico_lead(lead_id);

-- ============================================================
-- TRIGGERS — atualizado_em
-- ============================================================

CREATE OR REPLACE FUNCTION crm.fn_atualizar_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.atualizado_em = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_crm_documento_ts
    BEFORE UPDATE ON crm.documento
    FOR EACH ROW EXECUTE FUNCTION crm.fn_atualizar_timestamp();

CREATE TRIGGER trg_crm_lead_ts
    BEFORE UPDATE ON crm.lead
    FOR EACH ROW EXECUTE FUNCTION crm.fn_atualizar_timestamp();

CREATE TRIGGER trg_crm_nota_clinica_ts
    BEFORE UPDATE ON crm.nota_clinica
    FOR EACH ROW EXECUTE FUNCTION crm.fn_atualizar_timestamp();

-- ============================================================
-- TRIGGER — histórico automático de lead
-- ============================================================

CREATE OR REPLACE FUNCTION crm.fn_registrar_historico_lead()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO crm.historico_lead (lead_id, status_anterior, status_novo)
        VALUES (NEW.id, OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_crm_historico_lead
    AFTER UPDATE ON crm.lead
    FOR EACH ROW EXECUTE FUNCTION crm.fn_registrar_historico_lead();

-- ============================================================
-- VIEWS ANALÍTICAS (leitura sobre schema public)
-- ============================================================

-- Visão 360° do paciente: dados principais + métricas de agendamento e financeiro
CREATE OR REPLACE VIEW crm.v_paciente_360 AS
SELECT
    p.id                                                        AS paciente_id,
    p.nome,
    p.email,
    p.telefone,
    p.cpf,
    DATE_PART('year', AGE(p.data_nascimento))::INTEGER          AS idade,
    p.ativo,
    p.criado_em                                                 AS cadastrado_em,
    -- agendamentos
    COUNT(a.id)                                                 AS total_agendamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')           AS consultas_concluidas,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')           AS cancelamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')             AS no_shows,
    MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') AS ultima_consulta,
    -- financeiro
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS ltv_total,
    COALESCE(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS ticket_medio,
    COUNT(pg.id) FILTER (WHERE pg.metodo = 'PIX')               AS pagamentos_pix,
    COUNT(pg.id) FILTER (WHERE pg.metodo = 'CARTAO_CREDITO')    AS pagamentos_cartao
FROM public.paciente p
LEFT JOIN public.agendamento a   ON a.paciente_id = p.id
LEFT JOIN public.pagamento   pg  ON pg.paciente_id = p.id
GROUP BY p.id, p.nome, p.email, p.telefone, p.cpf,
         p.data_nascimento, p.ativo, p.criado_em;

COMMENT ON VIEW crm.v_paciente_360 IS
    'Visão consolidada por paciente: dados cadastrais + KPIs de agendamento e financeiro.';

-- ─────────────────────────────────────────────────────────────
-- Pacientes em risco de churn (sem consulta concluída há 90+ dias)
CREATE OR REPLACE VIEW crm.v_churn_risco AS
SELECT
    p.id            AS paciente_id,
    p.nome,
    p.email,
    p.telefone,
    MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') AS ultima_consulta,
    NOW() - MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') AS tempo_inativo,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')              AS total_consultas
FROM public.paciente p
JOIN public.agendamento a ON a.paciente_id = p.id
WHERE p.ativo = TRUE
GROUP BY p.id, p.nome, p.email, p.telefone
HAVING MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO')
       < NOW() - INTERVAL '90 days'
ORDER BY ultima_consulta ASC;

COMMENT ON VIEW crm.v_churn_risco IS
    'Pacientes ativos sem consulta concluída há mais de 90 dias (risco de churn).';

-- ─────────────────────────────────────────────────────────────
-- Funil de conversão de agendamentos por médico
CREATE OR REPLACE VIEW crm.v_funil_medico AS
SELECT
    m.id                                                        AS medico_id,
    m.nome                                                      AS medico_nome,
    m.especialidade,
    COUNT(a.id)                                                 AS total,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')           AS concluidos,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')           AS cancelados,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')             AS no_shows,
    ROUND(
        COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')::NUMERIC
        / NULLIF(COUNT(a.id), 0) * 100, 2
    )                                                           AS taxa_conversao_pct,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS receita_total
FROM public.medico m
LEFT JOIN public.agendamento a  ON a.medico_id = m.id
LEFT JOIN public.pagamento   pg ON pg.id = a.pagamento_id
WHERE m.ativo = TRUE
GROUP BY m.id, m.nome, m.especialidade
ORDER BY receita_total DESC;

COMMENT ON VIEW crm.v_funil_medico IS
    'Funil de conversão e receita por médico: total, concluídos, cancelados, no-show e taxa de conversão.';

-- ─────────────────────────────────────────────────────────────
-- KPIs de faturamento por período (mensal)
CREATE OR REPLACE VIEW crm.v_faturamento_mensal AS
SELECT
    DATE_TRUNC('month', pg.criado_em)::DATE                     AS mes,
    COUNT(pg.id)                                                 AS total_transacoes,
    COUNT(pg.id) FILTER (WHERE pg.status = 'APROVADO')          AS aprovados,
    COUNT(pg.id) FILTER (WHERE pg.status IN ('RECUSADO','CANCELADO')) AS recusados,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS receita_bruta,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.metodo = 'PIX'
                                     AND pg.status = 'APROVADO'), 0)   AS receita_pix,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.metodo = 'CARTAO_CREDITO'
                                     AND pg.status = 'APROVADO'), 0)   AS receita_cartao,
    COALESCE(SUM(r.valor)  FILTER (WHERE r.status  = 'CONCLUIDO'), 0)  AS total_reembolsos,
    ROUND(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 2)      AS ticket_medio
FROM public.pagamento pg
LEFT JOIN public.reembolso r ON r.pagamento_id = pg.id
GROUP BY DATE_TRUNC('month', pg.criado_em)
ORDER BY mes DESC;

COMMENT ON VIEW crm.v_faturamento_mensal IS
    'Faturamento mensal agregado: receita bruta, PIX vs cartão, reembolsos e ticket médio.';

-- ─────────────────────────────────────────────────────────────
-- Pacientes de alto valor (LTV no top 20%)
CREATE OR REPLACE VIEW crm.v_alto_valor AS
SELECT
    p.id            AS paciente_id,
    p.nome,
    p.email,
    p.telefone,
    SUM(pg.valor)   AS ltv_total,
    COUNT(pg.id)    AS total_pagamentos,
    NTILE(5) OVER (ORDER BY SUM(pg.valor))  AS quintil  -- 5 = top 20%
FROM public.paciente p
JOIN public.pagamento pg ON pg.paciente_id = p.id
WHERE pg.status = 'APROVADO'
  AND p.ativo = TRUE
GROUP BY p.id, p.nome, p.email, p.telefone
HAVING SUM(pg.valor) > 0
ORDER BY ltv_total DESC;

COMMENT ON VIEW crm.v_alto_valor IS
    'Pacientes ordenados por LTV. Quintil 5 = top 20% em valor.';

-- ─────────────────────────────────────────────────────────────
-- Análise de cancelamentos por origem e motivo
CREATE OR REPLACE VIEW crm.v_cancelamentos AS
SELECT
    a.origem_cancelamento,
    DATE_TRUNC('month', a.atualizado_em)::DATE  AS mes,
    COUNT(*)                                     AS total_cancelamentos,
    COUNT(DISTINCT a.paciente_id)                AS pacientes_distintos,
    COUNT(DISTINCT a.medico_id)                  AS medicos_distintos
FROM public.agendamento a
WHERE a.status = 'CANCELADO'
  AND a.origem_cancelamento IS NOT NULL
GROUP BY a.origem_cancelamento, DATE_TRUNC('month', a.atualizado_em)
ORDER BY mes DESC, total_cancelamentos DESC;

COMMENT ON VIEW crm.v_cancelamentos IS
    'Cancelamentos por origem (PACIENTE/MEDICO/ESTABELECIMENTO/SISTEMA) agrupados por mês.';

-- ─────────────────────────────────────────────────────────────
-- Ocupação da agenda por médico
CREATE OR REPLACE VIEW crm.v_ocupacao_agenda AS
SELECT
    m.id                                                        AS medico_id,
    m.nome                                                      AS medico_nome,
    m.especialidade,
    COUNT(a.id) FILTER (WHERE a.status NOT IN ('CANCELADO','NO_SHOW')) AS slots_usados,
    COUNT(a.id)                                                  AS slots_totais,
    ROUND(
        COUNT(a.id) FILTER (WHERE a.status NOT IN ('CANCELADO','NO_SHOW'))::NUMERIC
        / NULLIF(COUNT(a.id), 0) * 100, 2
    )                                                           AS taxa_ocupacao_pct
FROM public.medico m
LEFT JOIN public.agendamento a ON a.medico_id = m.id
WHERE m.ativo = TRUE
GROUP BY m.id, m.nome, m.especialidade
ORDER BY taxa_ocupacao_pct DESC NULLS LAST;

COMMENT ON VIEW crm.v_ocupacao_agenda IS
    'Taxa de ocupação por médico: slots efetivamente usados vs total agendado.';

-- ─────────────────────────────────────────────────────────────
-- Usuários do auth com dados de acesso (para CRM de usuários)
CREATE OR REPLACE VIEW crm.v_usuarios_crm AS
SELECT
    u.id                AS usuario_id,
    u.email,
    u.tipo_perfil,
    u.referencia_id,
    u.ativo,
    u.criado_em         AS cadastrado_em,
    ARRAY_AGG(r.nome)   AS roles,
    COUNT(rt.id) FILTER (WHERE rt.revogado = FALSE
                           AND rt.expira_em > NOW()) AS sessoes_ativas
FROM auth.usuario u
LEFT JOIN auth.usuario_role ur  ON ur.usuario_id = u.id
LEFT JOIN auth.role r           ON r.id = ur.role_id
LEFT JOIN auth.refresh_token rt ON rt.usuario_id = u.id
GROUP BY u.id, u.email, u.tipo_perfil, u.referencia_id, u.ativo, u.criado_em;

COMMENT ON VIEW crm.v_usuarios_crm IS
    'Usuários do auth com roles e sessões ativas. Base para relatórios de acesso no CRM.';

-- ============================================================
-- MATERIALIZED VIEW — Resumo executivo (refresh periódico)
-- ============================================================

CREATE MATERIALIZED VIEW crm.mv_resumo_executivo AS
SELECT
    -- agendamentos
    COUNT(a.id)                                                  AS total_agendamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')            AS concluidos,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')            AS cancelados,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')              AS no_shows,
    ROUND(
        COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')::NUMERIC
        / NULLIF(COUNT(a.id), 0) * 100, 2
    )                                                            AS taxa_conversao_pct,
    -- financeiro
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS receita_total,
    COALESCE(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS ticket_medio_geral,
    -- pacientes
    COUNT(DISTINCT p.id)                                         AS total_pacientes,
    COUNT(DISTINCT p.id) FILTER (WHERE p.criado_em >= NOW() - INTERVAL '30 days')
                                                                 AS novos_pacientes_30d,
    -- médicos
    COUNT(DISTINCT m.id) FILTER (WHERE m.ativo = TRUE)           AS medicos_ativos,
    -- atualização
    NOW()                                                        AS atualizado_em
FROM public.paciente p
CROSS JOIN public.medico m
LEFT JOIN public.agendamento a  ON a.paciente_id = p.id
LEFT JOIN public.pagamento   pg ON pg.paciente_id = p.id
WITH NO DATA;

-- Popula na primeira vez
REFRESH MATERIALIZED VIEW crm.mv_resumo_executivo;

COMMENT ON MATERIALIZED VIEW crm.mv_resumo_executivo IS
    'Resumo executivo com KPIs globais. '
    'Atualizar periodicamente com: REFRESH MATERIALIZED VIEW crm.mv_resumo_executivo;';

-- ============================================================
-- FUNÇÃO UTILITÁRIA — gerar texto de documento para embedding
-- ============================================================

CREATE OR REPLACE FUNCTION crm.fn_gerar_texto_paciente(p_paciente_id UUID)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_texto TEXT;
BEGIN
    SELECT
        FORMAT(
            'Paciente: %s. CPF: %s. Idade: %s anos. Email: %s. Telefone: %s. '
            'Cadastrado em: %s. '
            'Total de agendamentos: %s. Consultas concluidas: %s. Cancelamentos: %s. No-shows: %s. '
            'Ultima consulta: %s. '
            'LTV total: R$ %s. Ticket medio: R$ %s. '
            'Pagamentos por PIX: %s. Pagamentos por cartao: %s.',
            v360.nome,
            p.cpf,
            v360.idade,
            v360.email,
            COALESCE(v360.telefone, 'nao informado'),
            TO_CHAR(v360.cadastrado_em, 'DD/MM/YYYY'),
            v360.total_agendamentos,
            v360.consultas_concluidas,
            v360.cancelamentos,
            v360.no_shows,
            COALESCE(TO_CHAR(v360.ultima_consulta, 'DD/MM/YYYY'), 'nenhuma'),
            TO_CHAR(v360.ltv_total, 'FM999G999G990D00'),
            TO_CHAR(v360.ticket_medio, 'FM999G990D00'),
            v360.pagamentos_pix,
            v360.pagamentos_cartao
        )
    INTO v_texto
    FROM crm.v_paciente_360 v360
    JOIN public.paciente p ON p.id = v360.paciente_id
    WHERE v360.paciente_id = p_paciente_id;

    RETURN COALESCE(v_texto, 'Paciente nao encontrado.');
END;
$$;

COMMENT ON FUNCTION crm.fn_gerar_texto_paciente IS
    'Gera o texto composto de um paciente para ser salvo em crm.documento. '
    'Chamada pelo sgsm-ia ao consumir eventos do RabbitMQ. '
    'Após salvar o texto, o sgsm-ia chama a LLM API para gerar o embedding '
    'e indexa no Milvus, atualizando milvus_id e status_indexacao = INDEXADO.';

-- ============================================================
-- VERIFICAÇÃO FINAL
-- ============================================================

DO $$
BEGIN
    RAISE NOTICE '=============================================';
    RAISE NOTICE 'Schema CRM criado com sucesso.';
    RAISE NOTICE 'Tabelas : documento, tag_paciente, contato_paciente,';
    RAISE NOTICE '          lead, nota_clinica, kpi_snapshot, historico_lead';
    RAISE NOTICE 'Views   : v_paciente_360, v_churn_risco, v_funil_medico,';
    RAISE NOTICE '          v_faturamento_mensal, v_alto_valor, v_cancelamentos,';
    RAISE NOTICE '          v_ocupacao_agenda, v_usuarios_crm';
    RAISE NOTICE 'Mat.View: mv_resumo_executivo';
    RAISE NOTICE 'Funcao  : crm.fn_gerar_texto_paciente(uuid)';
    RAISE NOTICE '=============================================';
    RAISE NOTICE 'Proximo passo: instalar sgsm-ia (porta 8082)';
    RAISE NOTICE '  Vetores ficam no Milvus (porta 19530).';
    RAISE NOTICE '  Eventos via RabbitMQ (porta 5672).';
    RAISE NOTICE '  SEM pgvector -- extensao vector nao e necessaria.';
    RAISE NOTICE '=============================================';
END;
$$;
