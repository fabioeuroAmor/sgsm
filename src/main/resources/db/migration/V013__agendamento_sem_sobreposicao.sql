-- =============================================================================
-- V013 - Garantia no banco contra dois agendamentos no mesmo horário
-- Descrição: Plano_Desenvolvimento_Conflito_Agendamento_SGSM.docx, Fase 2. Impede
--            que o mesmo médico tenha dois agendamentos ativos com horários
--            sobrepostos, venha de qualquer canal (secretária, chatbot, WhatsApp,
--            paciente) e com qualquer número de instâncias do sgsm. Cancelados e
--            faltas (NO_SHOW) não contam. O sgsm traduz a violação (SQLState
--            23P01) em 409 "Horário indisponível".
--
-- ANTES DE APLICAR: rodar db/diagnostico/diagnostico_sobreposicao_agendamentos.sql
-- e resolver as sobreposições encontradas (a constraint não é criada se houver
-- alguma). CREATE EXTENSION exige superusuário: em hom/prd, criar a extensão
-- antes com o usuário administrador do banco.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE sgsm.agendamento
    ADD CONSTRAINT excl_sem_conflito_medico
    EXCLUDE USING gist (
        medico_id WITH =,
        tstzrange(data_hora_inicio, data_hora_fim, '[)') WITH &&
    ) WHERE (status NOT IN ('CANCELADO', 'NO_SHOW'));
