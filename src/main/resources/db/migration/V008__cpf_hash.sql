-- =============================================================================
-- V008 - Criptografia de CPF em repouso (item 2 do plano de compliance)
-- Descrição: sgsm.paciente.cpf passa a armazenar o valor cifrado (AES-256-GCM,
--            ver CpfCryptoConverter). Como a cifra é não-determinística, a
--            constraint de unicidade original em "cpf" deixa de fazer sentido
--            (dois CPFs iguais cifram para valores diferentes) e é substituída
--            por um índice único sobre "cpf_hash" (HMAC-SHA256, determinístico).
--            As linhas já existentes (CPF em texto puro) são migradas em
--            memória pelo CpfBackfillRunner no primeiro start da aplicação após
--            esta migration — não é preciso rodar UPDATE manual aqui.
--
--            A view crm.v_paciente_360 expõe p.cpf diretamente (consumida via
--            SELECT * em CrmService.paciente360() no sgsm-ia) — precisa ser
--            recriada sem a coluna, senão passaria a vazar o valor cifrado
--            (ilegível) no lugar do CPF. Nota: a definição abaixo reflete a
--            view como está hoje em produção/dev (colunas paciente_id, telefone,
--            idade, cadastrado_em etc.), que já diverge da versão original em
--            V004__schema_crm.sql — aparentemente alterada fora do fluxo de
--            migrations rastreadas.
-- =============================================================================

DROP VIEW IF EXISTS crm.v_paciente_360;

ALTER TABLE sgsm.paciente ALTER COLUMN cpf TYPE VARCHAR(255);
ALTER TABLE sgsm.paciente DROP CONSTRAINT paciente_cpf_key;

ALTER TABLE sgsm.paciente ADD COLUMN cpf_hash VARCHAR(64);
CREATE UNIQUE INDEX idx_paciente_cpf_hash ON sgsm.paciente(cpf_hash);

CREATE VIEW crm.v_paciente_360 AS
SELECT p.id AS paciente_id,
    p.nome,
    p.email,
    p.telefone,
    date_part('year'::text, age(p.data_nascimento::timestamp with time zone))::integer AS idade,
    p.ativo,
    p.criado_em AS cadastrado_em,
    count(a.id) AS total_agendamentos,
    count(a.id) FILTER (WHERE a.status = 'CONCLUIDO'::sgsm.status_agendamento) AS consultas_concluidas,
    count(a.id) FILTER (WHERE a.status = 'CANCELADO'::sgsm.status_agendamento) AS cancelamentos,
    count(a.id) FILTER (WHERE a.status = 'NO_SHOW'::sgsm.status_agendamento) AS no_shows,
    max(a.data_hora_inicio) FILTER (WHERE a.status = 'CONCLUIDO'::sgsm.status_agendamento) AS ultima_consulta,
    COALESCE(sum(pg.valor) FILTER (WHERE pg.status = 'APROVADO'::sgsm.status_pagamento), 0::numeric) AS ltv_total,
    COALESCE(avg(pg.valor) FILTER (WHERE pg.status = 'APROVADO'::sgsm.status_pagamento), 0::numeric) AS ticket_medio,
    count(pg.id) FILTER (WHERE pg.metodo = 'PIX'::sgsm.metodo_pagamento) AS pagamentos_pix,
    count(pg.id) FILTER (WHERE pg.metodo = 'CARTAO_CREDITO'::sgsm.metodo_pagamento) AS pagamentos_cartao
   FROM sgsm.paciente p
     LEFT JOIN sgsm.agendamento a ON a.paciente_id = p.id
     LEFT JOIN sgsm.pagamento pg ON pg.paciente_id = p.id
  GROUP BY p.id, p.nome, p.email, p.telefone, p.data_nascimento, p.ativo, p.criado_em;
