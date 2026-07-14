-- ============================================================
-- Recriar FKs das tabelas crm.* apontando para sgsm.*
-- ============================================================

ALTER TABLE crm.tag_paciente
    ADD CONSTRAINT tag_paciente_paciente_id_fkey
    FOREIGN KEY (paciente_id) REFERENCES sgsm.paciente(id) ON DELETE CASCADE;

ALTER TABLE crm.contato_paciente
    ADD CONSTRAINT contato_paciente_paciente_id_fkey
    FOREIGN KEY (paciente_id) REFERENCES sgsm.paciente(id);

ALTER TABLE crm.contato_paciente
    ADD CONSTRAINT contato_paciente_agendamento_id_fkey
    FOREIGN KEY (agendamento_id) REFERENCES sgsm.agendamento(id);

ALTER TABLE crm.lead
    ADD CONSTRAINT lead_convertido_paciente_id_fkey
    FOREIGN KEY (convertido_paciente_id) REFERENCES sgsm.paciente(id);

ALTER TABLE crm.nota_clinica
    ADD CONSTRAINT nota_clinica_agendamento_id_fkey
    FOREIGN KEY (agendamento_id) REFERENCES sgsm.agendamento(id);

ALTER TABLE crm.nota_clinica
    ADD CONSTRAINT nota_clinica_paciente_id_fkey
    FOREIGN KEY (paciente_id) REFERENCES sgsm.paciente(id);

ALTER TABLE crm.nota_clinica
    ADD CONSTRAINT nota_clinica_medico_id_fkey
    FOREIGN KEY (medico_id) REFERENCES sgsm.medico(id);

ALTER TABLE crm.kpi_snapshot
    ADD CONSTRAINT kpi_snapshot_medico_id_fkey
    FOREIGN KEY (medico_id) REFERENCES sgsm.medico(id);

ALTER TABLE crm.kpi_snapshot
    ADD CONSTRAINT kpi_snapshot_estabelecimento_id_fkey
    FOREIGN KEY (estabelecimento_id) REFERENCES sgsm.estabelecimento(id);

-- ============================================================
-- Recriar views analiticas apontando para sgsm.*
-- ============================================================

CREATE OR REPLACE VIEW crm.v_paciente_360 AS
SELECT
    p.id                                                        AS paciente_id,
    p.nome, p.email, p.telefone, p.cpf,
    DATE_PART('year', AGE(p.data_nascimento))::INTEGER          AS idade,
    p.ativo,
    p.criado_em                                                 AS cadastrado_em,
    COUNT(a.id)                                                 AS total_agendamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')           AS consultas_concluidas,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')           AS cancelamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')             AS no_shows,
    MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') AS ultima_consulta,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS ltv_total,
    COALESCE(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS ticket_medio,
    COUNT(pg.id) FILTER (WHERE pg.metodo = 'PIX')               AS pagamentos_pix,
    COUNT(pg.id) FILTER (WHERE pg.metodo = 'CARTAO_CREDITO')    AS pagamentos_cartao
FROM sgsm.paciente p
LEFT JOIN sgsm.agendamento a  ON a.paciente_id = p.id
LEFT JOIN sgsm.pagamento   pg ON pg.paciente_id = p.id
GROUP BY p.id, p.nome, p.email, p.telefone, p.cpf,
         p.data_nascimento, p.ativo, p.criado_em;

CREATE OR REPLACE VIEW crm.v_churn_risco AS
SELECT
    p.id AS paciente_id, p.nome, p.email, p.telefone,
    MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') AS ultima_consulta,
    NOW() - MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO') AS tempo_inativo,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO') AS total_consultas
FROM sgsm.paciente p
JOIN sgsm.agendamento a ON a.paciente_id = p.id
WHERE p.ativo = TRUE
GROUP BY p.id, p.nome, p.email, p.telefone
HAVING MAX(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO')
       < NOW() - INTERVAL '90 days'
ORDER BY ultima_consulta ASC;

CREATE OR REPLACE VIEW crm.v_funil_medico AS
SELECT
    m.id AS medico_id, m.nome AS medico_nome, m.especialidade,
    COUNT(a.id)                                                 AS total,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')           AS concluidos,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')           AS cancelados,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')             AS no_shows,
    ROUND(
        COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')::NUMERIC
        / NULLIF(COUNT(a.id), 0) * 100, 2
    )                                                           AS taxa_conversao_pct,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS receita_total
FROM sgsm.medico m
LEFT JOIN sgsm.agendamento a  ON a.medico_id = m.id
LEFT JOIN sgsm.pagamento   pg ON pg.id = a.pagamento_id
WHERE m.ativo = TRUE
GROUP BY m.id, m.nome, m.especialidade
ORDER BY receita_total DESC;

CREATE OR REPLACE VIEW crm.v_faturamento_mensal AS
SELECT
    DATE_TRUNC('month', pg.criado_em)::DATE                     AS mes,
    COUNT(pg.id)                                                 AS total_transacoes,
    COUNT(pg.id) FILTER (WHERE pg.status = 'APROVADO')          AS aprovados,
    COUNT(pg.id) FILTER (WHERE pg.status IN ('RECUSADO','CANCELADO')) AS recusados,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0)  AS receita_bruta,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.metodo = 'PIX'
                                     AND pg.status = 'APROVADO'), 0)  AS receita_pix,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.metodo = 'CARTAO_CREDITO'
                                     AND pg.status = 'APROVADO'), 0)  AS receita_cartao,
    COALESCE(SUM(r.valor)  FILTER (WHERE r.status  = 'CONCLUIDO'), 0) AS total_reembolsos,
    ROUND(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 2)     AS ticket_medio
FROM sgsm.pagamento pg
LEFT JOIN sgsm.reembolso r ON r.pagamento_id = pg.id
GROUP BY DATE_TRUNC('month', pg.criado_em)
ORDER BY mes DESC;

CREATE OR REPLACE VIEW crm.v_alto_valor AS
SELECT
    p.id AS paciente_id, p.nome, p.email, p.telefone,
    SUM(pg.valor) AS ltv_total,
    COUNT(pg.id)  AS total_pagamentos,
    NTILE(5) OVER (ORDER BY SUM(pg.valor)) AS quintil
FROM sgsm.paciente p
JOIN sgsm.pagamento pg ON pg.paciente_id = p.id
WHERE pg.status = 'APROVADO' AND p.ativo = TRUE
GROUP BY p.id, p.nome, p.email, p.telefone
HAVING SUM(pg.valor) > 0
ORDER BY ltv_total DESC;

CREATE OR REPLACE VIEW crm.v_cancelamentos AS
SELECT
    a.origem_cancelamento,
    DATE_TRUNC('month', a.atualizado_em)::DATE AS mes,
    COUNT(*)                                   AS total_cancelamentos,
    COUNT(DISTINCT a.paciente_id)              AS pacientes_distintos,
    COUNT(DISTINCT a.medico_id)                AS medicos_distintos
FROM sgsm.agendamento a
WHERE a.status = 'CANCELADO' AND a.origem_cancelamento IS NOT NULL
GROUP BY a.origem_cancelamento, DATE_TRUNC('month', a.atualizado_em)
ORDER BY mes DESC, total_cancelamentos DESC;

CREATE OR REPLACE VIEW crm.v_ocupacao_agenda AS
SELECT
    m.id AS medico_id, m.nome AS medico_nome, m.especialidade,
    COUNT(a.id) FILTER (WHERE a.status NOT IN ('CANCELADO','NO_SHOW')) AS slots_usados,
    COUNT(a.id)                                                         AS slots_totais,
    ROUND(
        COUNT(a.id) FILTER (WHERE a.status NOT IN ('CANCELADO','NO_SHOW'))::NUMERIC
        / NULLIF(COUNT(a.id), 0) * 100, 2
    ) AS taxa_ocupacao_pct
FROM sgsm.medico m
LEFT JOIN sgsm.agendamento a ON a.medico_id = m.id
WHERE m.ativo = TRUE
GROUP BY m.id, m.nome, m.especialidade
ORDER BY taxa_ocupacao_pct DESC NULLS LAST;

-- ============================================================
-- Recriar materialized view
-- ============================================================

CREATE MATERIALIZED VIEW crm.mv_resumo_executivo AS
SELECT
    COUNT(a.id)                                                   AS total_agendamentos,
    COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')             AS concluidos,
    COUNT(a.id) FILTER (WHERE a.status = 'CANCELADO')             AS cancelados,
    COUNT(a.id) FILTER (WHERE a.status = 'NO_SHOW')               AS no_shows,
    ROUND(
        COUNT(a.id) FILTER (WHERE a.status = 'CONCLUIDO')::NUMERIC
        / NULLIF(COUNT(a.id), 0) * 100, 2
    )                                                             AS taxa_conversao_pct,
    COALESCE(SUM(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS receita_total,
    COALESCE(AVG(pg.valor) FILTER (WHERE pg.status = 'APROVADO'), 0) AS ticket_medio_geral,
    COUNT(DISTINCT p.id)                                          AS total_pacientes,
    COUNT(DISTINCT p.id) FILTER (WHERE p.criado_em >= NOW() - INTERVAL '30 days')
                                                                  AS novos_pacientes_30d,
    COUNT(DISTINCT m.id) FILTER (WHERE m.ativo = TRUE)            AS medicos_ativos,
    NOW()                                                         AS atualizado_em
FROM sgsm.paciente p
CROSS JOIN sgsm.medico m
LEFT JOIN sgsm.agendamento a  ON a.paciente_id = p.id
LEFT JOIN sgsm.pagamento   pg ON pg.paciente_id = p.id
WITH NO DATA;

REFRESH MATERIALIZED VIEW crm.mv_resumo_executivo;

-- ============================================================
-- Recriar funcao utilitaria apontando para sgsm.*
-- ============================================================

CREATE OR REPLACE FUNCTION crm.fn_gerar_texto_paciente(p_paciente_id UUID)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE v_texto TEXT;
BEGIN
    SELECT FORMAT(
        'Paciente: %s. CPF: %s. Idade: %s anos. Email: %s. Telefone: %s. '
        'Cadastrado em: %s. '
        'Total de agendamentos: %s. Consultas concluidas: %s. Cancelamentos: %s. No-shows: %s. '
        'Ultima consulta: %s. '
        'LTV total: R$ %s. Ticket medio: R$ %s. '
        'Pagamentos por PIX: %s. Pagamentos por cartao: %s.',
        v360.nome, p.cpf, v360.idade, v360.email,
        COALESCE(v360.telefone, 'nao informado'),
        TO_CHAR(v360.cadastrado_em, 'DD/MM/YYYY'),
        v360.total_agendamentos, v360.consultas_concluidas,
        v360.cancelamentos, v360.no_shows,
        COALESCE(TO_CHAR(v360.ultima_consulta, 'DD/MM/YYYY'), 'nenhuma'),
        TO_CHAR(v360.ltv_total, 'FM999G999G990D00'),
        TO_CHAR(v360.ticket_medio, 'FM999G990D00'),
        v360.pagamentos_pix, v360.pagamentos_cartao
    )
    INTO v_texto
    FROM crm.v_paciente_360 v360
    JOIN sgsm.paciente p ON p.id = v360.paciente_id
    WHERE v360.paciente_id = p_paciente_id;
    RETURN COALESCE(v_texto, 'Paciente nao encontrado.');
END;
$$;
