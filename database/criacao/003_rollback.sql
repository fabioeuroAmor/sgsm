-- =============================================================================
-- SGSM - Sistema de Gerenciamento de Serviços Médicos
-- Script 003 - Rollback (DROP de tudo)
-- ATENÇÃO: apaga todos os dados. Use apenas em ambiente local/dev.
-- =============================================================================

-- Tabelas de histórico e detalhe (sem dependentes)
DROP TABLE IF EXISTS public.historico_status_agendamento CASCADE;
DROP TABLE IF EXISTS public.historico_status_pagamento    CASCADE;
DROP TABLE IF EXISTS public.pagamento_cartao              CASCADE;
DROP TABLE IF EXISTS public.pagamento_pix                 CASCADE;
DROP TABLE IF EXISTS public.reembolso                     CASCADE;

-- Agendamento e bloqueios
DROP TABLE IF EXISTS public.agendamento    CASCADE;
DROP TABLE IF EXISTS public.bloqueio_agenda CASCADE;

-- Pagamento
DROP TABLE IF EXISTS public.pagamento CASCADE;

-- Agenda
DROP TABLE IF EXISTS public.agenda_medico CASCADE;

-- Vínculos N:N
DROP TABLE IF EXISTS public.medico_estabelecimento CASCADE;

-- Entidades principais
DROP TABLE IF EXISTS public.servico_medico   CASCADE;
DROP TABLE IF EXISTS public.estabelecimento  CASCADE;
DROP TABLE IF EXISTS public.paciente         CASCADE;
DROP TABLE IF EXISTS public.medico           CASCADE;

-- Tipos ENUM
DROP TYPE IF EXISTS public.tipo_bloqueio        CASCADE;
DROP TYPE IF EXISTS public.tipo_agendamento     CASCADE;
DROP TYPE IF EXISTS public.status_reembolso     CASCADE;
DROP TYPE IF EXISTS public.status_pagamento     CASCADE;
DROP TYPE IF EXISTS public.status_agendamento   CASCADE;
DROP TYPE IF EXISTS public.origem_cancelamento  CASCADE;
DROP TYPE IF EXISTS public.metodo_pagamento     CASCADE;
DROP TYPE IF EXISTS public.dia_semana           CASCADE;
