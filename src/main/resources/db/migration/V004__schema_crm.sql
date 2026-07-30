-- =============================================================================
-- V004 - Schema CRM
-- Descrição: Cria ENUMs, tabelas, views, materialized view, triggers e funções
--            do schema crm para CRM analítico/operacional e rastreamento de
--            indexação vetorial no Milvus (campo milvus_id — NÃO usa pgvector).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ENUMs
-- -----------------------------------------------------------------------------
DO $$ BEGIN
    CREATE TYPE crm.tipo_documento   AS ENUM ('PACIENTE','MEDICO','SERVICO','AGENDAMENTO','ESTABELECIMENTO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.status_indexacao AS ENUM ('PENDENTE','INDEXADO','ERRO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.tipo_contato     AS ENUM ('LIGACAO','EMAIL','WHATSAPP','SMS','PRESENCIAL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.direcao_contato  AS ENUM ('ENTRADA','SAIDA');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.status_lead      AS ENUM ('NOVO','CONTATADO','QUALIFICADO','CONVERTIDO','PERDIDO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.origem_lead      AS ENUM ('SITE','INDICACAO','REDES_SOCIAIS','TELEFONE','CAMPANHA','OUTRO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.tipo_nota        AS ENUM ('ANAMNESE','EVOLUCAO','OBSERVACAO','RETORNO','OUTRO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.tipo_tag         AS ENUM ('MANUAL','AUTOMATICO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE crm.periodo_kpi      AS ENUM ('DIARIO','SEMANAL','MENSAL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- -----------------------------------------------------------------------------
-- Função utilitária de timestamp (usada por triggers)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION crm.fn_atualizar_timestamp()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.atualizado_em = NOW();
    RETURN NEW;
END;
$$;

-- -----------------------------------------------------------------------------
-- crm.documento — metadados + rastreamento de indexação no Milvus
-- O vetor fica EXCLUSIVAMENTE no Milvus; aqui ficam o texto e o milvus_id.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm.documento (
    id                 UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo               crm.tipo_documento       NOT NULL,
    referencia_id      UUID                     NOT NULL,
    referencia_tabela  VARCHAR(100)             NOT NULL,
    conteudo           TEXT                     NOT NULL,
    milvus_id          VARCHAR(36),
    status_indexacao   crm.status_indexacao     NOT NULL DEFAULT 'PENDENTE',
    tentativas         SMALLINT                 NOT NULL DEFAULT 0,
    versao             INTEGER                  NOT NULL DEFAULT 1,
    criado_em          TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    atualizado_em      TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_crm_documento_tipo_ref UNIQUE (tipo, referencia_id)
);

CREATE INDEX IF NOT EXISTS idx_crm_doc_status      ON crm.documento (status_indexacao);
CREATE INDEX IF NOT EXISTS idx_crm_doc_referencia  ON crm.documento (referencia_id);
CREATE INDEX IF NOT EXISTS idx_crm_doc_tipo        ON crm.documento (tipo);
CREATE INDEX IF NOT EXISTS idx_crm_doc_milvus      ON crm.documento (milvus_id) WHERE milvus_id IS NOT NULL;

CREATE OR REPLACE TRIGGER trg_crm_documento_ts
    BEFORE UPDATE ON crm.documento
    FOR EACH ROW EXECUTE FUNCTION crm.fn_atualizar_timestamp();

-- -----------------------------------------------------------------------------
-- crm.tag_paciente — segmentação manual ou automática de pacientes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm.tag_paciente (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id UUID         NOT NULL REFERENCES sgsm.paciente(id) ON DELETE CASCADE,
    tag         VARCHAR(100) NOT NULL,
    tipo        crm.tipo_tag NOT NULL DEFAULT 'MANUAL',
    criado_por  UUID         REFERENCES auth.usuario(id),
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_crm_tag_paciente UNIQUE (paciente_id, tag)
);

-- -----------------------------------------------------------------------------
-- crm.contato_paciente — histórico de interações (ligações, emails, etc.)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm.contato_paciente (
    id                 UUID                   PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id        UUID                   NOT NULL REFERENCES sgsm.paciente(id),
    tipo               crm.tipo_contato       NOT NULL,
    direcao            crm.direcao_contato    NOT NULL,
    descricao          TEXT                   NOT NULL,
    duracao_segundos   INTEGER                CHECK (duracao_segundos > 0),
    usuario_id         UUID                   REFERENCES auth.usuario(id),
    agendamento_id     UUID                   REFERENCES sgsm.agendamento(id),
    criado_em          TIMESTAMPTZ            NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- crm.lead — prospects antes de se tornarem pacientes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm.lead (
    id                      UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                    VARCHAR(150)       NOT NULL,
    email                   VARCHAR(255),
    telefone                VARCHAR(20),
    interesse               TEXT,
    origem                  crm.origem_lead    NOT NULL DEFAULT 'OUTRO',
    status                  crm.status_lead    NOT NULL DEFAULT 'NOVO',
    convertido_paciente_id  UUID               REFERENCES sgsm.paciente(id),
    responsavel_id          UUID               REFERENCES auth.usuario(id),
    observacoes             TEXT,
    criado_em               TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    atualizado_em           TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE TRIGGER trg_crm_lead_ts
    BEFORE UPDATE ON crm.lead
    FOR EACH ROW EXECUTE FUNCTION crm.fn_atualizar_timestamp();

-- -----------------------------------------------------------------------------
-- crm.historico_lead — funil: populado automaticamente por trigger
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm.historico_lead (
    id              UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id         UUID               NOT NULL REFERENCES crm.lead(id) ON DELETE CASCADE,
    status_anterior crm.status_lead,
    status_novo     crm.status_lead    NOT NULL,
    observacao      VARCHAR(500),
    usuario_id      UUID               REFERENCES auth.usuario(id),
    criado_em       TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION crm.fn_registrar_historico_lead()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO crm.historico_lead (lead_id, status_anterior, status_novo)
        VALUES (NEW.id, OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_crm_historico_lead
    AFTER UPDATE ON crm.lead
    FOR EACH ROW EXECUTE FUNCTION crm.fn_registrar_historico_lead();

-- -----------------------------------------------------------------------------
-- crm.nota_clinica — anotações estruturadas por atendimento (campo semântico RAG)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm.nota_clinica (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    agendamento_id  UUID           NOT NULL REFERENCES sgsm.agendamento(id),
    paciente_id     UUID           NOT NULL REFERENCES sgsm.paciente(id),
    medico_id       UUID           NOT NULL REFERENCES sgsm.medico(id),
    tipo            crm.tipo_nota  NOT NULL DEFAULT 'OBSERVACAO',
    conteudo        TEXT           NOT NULL,
    criado_em       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE TRIGGER trg_crm_nota_clinica_ts
    BEFORE UPDATE ON crm.nota_clinica
    FOR EACH ROW EXECUTE FUNCTION crm.fn_atualizar_timestamp();

-- -----------------------------------------------------------------------------
-- crm.kpi_snapshot — snapshots pré-calculados de KPIs (DIARIO/SEMANAL/MENSAL)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm.kpi_snapshot (
    id                        UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    periodo                   crm.periodo_kpi   NOT NULL,
    data_referencia           DATE              NOT NULL,
    medico_id                 UUID              REFERENCES sgsm.medico(id),
    estabelecimento_id        UUID              REFERENCES sgsm.estabelecimento(id),
    especialidade             VARCHAR(100),
    total_agendamentos        INTEGER           NOT NULL DEFAULT 0,
    agendamentos_concluidos   INTEGER           NOT NULL DEFAULT 0,
    agendamentos_cancelados   INTEGER           NOT NULL DEFAULT 0,
    agendamentos_no_show      INTEGER           NOT NULL DEFAULT 0,
    taxa_conversao            NUMERIC(5,2)      NOT NULL DEFAULT 0,
    taxa_cancelamento         NUMERIC(5,2)      NOT NULL DEFAULT 0,
    taxa_no_show              NUMERIC(5,2)      NOT NULL DEFAULT 0,
    receita_bruta             NUMERIC(12,2)     NOT NULL DEFAULT 0,
    receita_pix               NUMERIC(12,2)     NOT NULL DEFAULT 0,
    receita_cartao            NUMERIC(12,2)     NOT NULL DEFAULT 0,
    total_reembolsos          NUMERIC(12,2)     NOT NULL DEFAULT 0,
    ticket_medio              NUMERIC(10,2)     NOT NULL DEFAULT 0,
    novos_pacientes           INTEGER           NOT NULL DEFAULT 0,
    pacientes_retorno         INTEGER           NOT NULL DEFAULT 0,
    criado_em                 TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_crm_kpi_snapshot UNIQUE (periodo, data_referencia, medico_id, estabelecimento_id, especialidade)
);

-- -----------------------------------------------------------------------------
-- VIEWS DO SCHEMA CRM
-- -----------------------------------------------------------------------------

-- Visão 360 do paciente: KPIs de agendamento + financeiro
CREATE OR REPLACE VIEW crm.v_paciente_360 AS
SELECT
    p.id,
    p.nome,
    p.email,
    p.cpf,
    p.data_nascimento,
    p.ativo,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')                                       AS consultas_concluidas,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')                                       AS cancelamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')                                         AS no_shows,
    MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO')                           AS ultima_consulta,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)                        AS ltv,
    COUNT(DISTINCT a.medico_id)                                                              AS medicos_distintos
FROM sgsm.paciente p
LEFT JOIN sgsm.agendamento a  ON a.paciente_id = p.id
LEFT JOIN sgsm.pagamento   pg ON pg.paciente_id = p.id
GROUP BY p.id, p.nome, p.email, p.cpf, p.data_nascimento, p.ativo;

-- Pacientes em risco de churn (sem consulta concluída há mais de 90 dias)
CREATE OR REPLACE VIEW crm.v_churn_risco AS
SELECT
    p.id,
    p.nome,
    p.email,
    MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') AS ultima_consulta,
    EXTRACT(DAY FROM NOW() - MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO'))::INTEGER AS dias_sem_consulta
FROM sgsm.paciente p
JOIN sgsm.agendamento a ON a.paciente_id = p.id
WHERE p.ativo = true
GROUP BY p.id, p.nome, p.email
HAVING MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') < NOW() - INTERVAL '90 days'
    OR MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') IS NULL;

-- Funil de conversão por médico
CREATE OR REPLACE VIEW crm.v_funil_medico AS
SELECT
    m.id          AS medico_id,
    m.nome        AS medico_nome,
    m.especialidade,
    COUNT(a.id)                                                      AS total_agendamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')               AS concluidos,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')               AS cancelados,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')                 AS no_shows,
    ROUND(COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')::NUMERIC
          / NULLIF(COUNT(a.id), 0) * 100, 2)                        AS taxa_conclusao,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS receita_total
FROM sgsm.medico m
LEFT JOIN sgsm.agendamento a  ON a.medico_id = m.id
LEFT JOIN sgsm.pagamento   pg ON pg.paciente_id = a.paciente_id
GROUP BY m.id, m.nome, m.especialidade;

-- Faturamento mensal
CREATE OR REPLACE VIEW crm.v_faturamento_mensal AS
SELECT
    DATE_TRUNC('month', pg.criado_em)::DATE            AS mes,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS receita_bruta,
    COALESCE(SUM(pc.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS receita_cartao,
    COALESCE(SUM(pp.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS receita_pix,
    COALESCE(SUM(r.valor)  FILTER (WHERE r.status  = 'CONCLUIDO'), 0) AS total_reembolsos,
    ROUND(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 2)     AS ticket_medio
FROM sgsm.pagamento pg
LEFT JOIN sgsm.pagamento_cartao pc ON pc.pagamento_id = pg.id
LEFT JOIN sgsm.pagamento_pix    pp ON pp.pagamento_id = pg.id
LEFT JOIN sgsm.reembolso        r  ON r.pagamento_id  = pg.id
GROUP BY DATE_TRUNC('month', pg.criado_em)::DATE
ORDER BY mes DESC;

-- Pacientes por LTV com quintil (quintil 5 = top 20%)
CREATE OR REPLACE VIEW crm.v_alto_valor AS
SELECT
    p.id,
    p.nome,
    p.email,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS ltv,
    NTILE(5) OVER (ORDER BY COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)) AS quintil
FROM sgsm.paciente p
LEFT JOIN sgsm.pagamento pg ON pg.paciente_id = p.id
WHERE p.ativo = true
GROUP BY p.id, p.nome, p.email;

-- Cancelamentos por origem e mês
CREATE OR REPLACE VIEW crm.v_cancelamentos AS
SELECT
    DATE_TRUNC('month', a.data_hora_inicio)::DATE AS mes,
    a.origem_cancelamento,
    COUNT(*) AS total
FROM sgsm.agendamento a
WHERE a.status = 'CANCELADO'
GROUP BY DATE_TRUNC('month', a.data_hora_inicio)::DATE, a.origem_cancelamento
ORDER BY mes DESC, total DESC;

-- Taxa de ocupação por médico
CREATE OR REPLACE VIEW crm.v_ocupacao_agenda AS
SELECT
    m.id          AS medico_id,
    m.nome        AS medico_nome,
    COUNT(a.id)                                                  AS agendamentos_usados,
    COUNT(a.id) FILTER (WHERE a.status IN ('CONCLUIDO','EM_ANDAMENTO','CONFIRMADO')) AS slots_ocupados,
    ROUND(
        COUNT(a.id) FILTER (WHERE a.status IN ('CONCLUIDO','EM_ANDAMENTO','CONFIRMADO'))::NUMERIC
        / NULLIF(COUNT(a.id), 0) * 100, 2
    ) AS taxa_ocupacao_pct
FROM sgsm.medico m
LEFT JOIN sgsm.agendamento a ON a.medico_id = m.id
GROUP BY m.id, m.nome;

-- Usuários do auth com roles e sessões ativas
CREATE OR REPLACE VIEW crm.v_usuarios_crm AS
SELECT
    u.id,
    u.email,
    u.tipo_perfil,
    u.ativo,
    u.criado_em,
    ARRAY_AGG(DISTINCT r.nome) AS roles,
    COUNT(rt.id) FILTER (WHERE rt.revogado = false AND rt.expira_em > NOW()) AS sessoes_ativas
FROM auth.usuario u
LEFT JOIN auth.usuario_role ur ON ur.usuario_id = u.id
LEFT JOIN auth.role         r  ON r.id = ur.role_id
LEFT JOIN auth.refresh_token rt ON rt.usuario_id = u.id
GROUP BY u.id, u.email, u.tipo_perfil, u.ativo, u.criado_em;

-- -----------------------------------------------------------------------------
-- MATERIALIZED VIEW — resumo executivo global
-- Atualizar com: REFRESH MATERIALIZED VIEW crm.mv_resumo_executivo;
-- Chamado pelo sgsm-ia via /ia/etl/sync
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS crm.mv_resumo_executivo AS
SELECT
    COUNT(DISTINCT p.id)                                                          AS total_pacientes,
    COUNT(DISTINCT m.id)                                                          AS total_medicos,
    COUNT(DISTINCT e.id)                                                          AS total_estabelecimentos,
    COUNT(a.id)                                                                   AS total_agendamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')                             AS agendamentos_concluidos,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')                             AS agendamentos_cancelados,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)              AS receita_total,
    ROUND(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 2)                 AS ticket_medio,
    COUNT(DISTINCT d.id) FILTER (WHERE d.status_indexacao = 'INDEXADO')           AS documentos_indexados,
    COUNT(DISTINCT d.id) FILTER (WHERE d.status_indexacao = 'PENDENTE')           AS documentos_pendentes,
    COUNT(DISTINCT d.id) FILTER (WHERE d.status_indexacao = 'ERRO')               AS documentos_com_erro,
    NOW()                                                                          AS atualizado_em
FROM sgsm.paciente       p
FULL OUTER JOIN sgsm.medico           m  ON true
FULL OUTER JOIN sgsm.estabelecimento  e  ON true
FULL OUTER JOIN sgsm.agendamento      a  ON true
FULL OUTER JOIN sgsm.pagamento        pg ON true
FULL OUTER JOIN crm.documento         d  ON true
WITH NO DATA;

-- Popula a materialized view pela primeira vez
REFRESH MATERIALIZED VIEW crm.mv_resumo_executivo;
