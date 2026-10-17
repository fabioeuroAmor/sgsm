-- =============================================================================
-- Diagnóstico pré-V013: agendamentos ativos do mesmo médico com horários sobrepostos.
-- Rodar em cada ambiente antes da V013 — a constraint excl_sem_conflito_medico não
-- é criada enquanto houver linhas aqui. Somente leitura.
--
-- Resolução (DA3 do plano): decidir caso a caso; normalmente cancelar o agendamento
-- mais novo que ainda não foi realizado (status PENDENTE/AGUARDANDO_PAGAMENTO),
-- registrando o motivo:
--   UPDATE sgsm.agendamento
--      SET status = 'CANCELADO', origem_cancelamento = 'ESTABELECIMENTO',
--          motivo_cancelamento = 'Duplicidade de horário (pré-V013)'
--    WHERE id = '<id>';
-- =============================================================================

SELECT a.medico_id,
       a.id                AS agendamento_a,
       a.status            AS status_a,
       a.criado_em         AS criado_a,
       b.id                AS agendamento_b,
       b.status            AS status_b,
       b.criado_em         AS criado_b,
       a.data_hora_inicio,
       a.data_hora_fim,
       b.data_hora_inicio  AS inicio_b,
       b.data_hora_fim     AS fim_b
FROM sgsm.agendamento a
JOIN sgsm.agendamento b
  ON b.medico_id = a.medico_id
 AND b.id > a.id
 AND tstzrange(a.data_hora_inicio, a.data_hora_fim, '[)') && tstzrange(b.data_hora_inicio, b.data_hora_fim, '[)')
WHERE a.status NOT IN ('CANCELADO', 'NO_SHOW')
  AND b.status NOT IN ('CANCELADO', 'NO_SHOW')
ORDER BY a.medico_id, a.data_hora_inicio;
