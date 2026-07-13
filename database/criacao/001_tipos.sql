-- =============================================================================
-- SGSM - Sistema de Gerenciamento de Serviços Médicos
-- Script 001 - Tipos ENUM
-- Executar ANTES de 002_tabelas.sql
-- =============================================================================

CREATE TYPE public.dia_semana AS ENUM (
    'SEGUNDA',
    'TERCA',
    'QUARTA',
    'QUINTA',
    'SEXTA',
    'SABADO',
    'DOMINGO'
);

CREATE TYPE public.metodo_pagamento AS ENUM (
    'PIX',
    'CARTAO_CREDITO'
);

CREATE TYPE public.origem_cancelamento AS ENUM (
    'PACIENTE',
    'MEDICO',
    'ESTABELECIMENTO',
    'SISTEMA'
);

CREATE TYPE public.status_agendamento AS ENUM (
    'PENDENTE',
    'AGUARDANDO_PAGAMENTO',
    'CONFIRMADO',
    'EM_ANDAMENTO',
    'CONCLUIDO',
    'CANCELADO',
    'NO_SHOW',
    'A_CAMINHO',
    'CHEGOU'
);

CREATE TYPE public.status_pagamento AS ENUM (
    'PENDENTE',
    'PROCESSANDO',
    'APROVADO',
    'RECUSADO',
    'CANCELADO',
    'REEMBOLSADO'
);

CREATE TYPE public.status_reembolso AS ENUM (
    'SOLICITADO',
    'PROCESSANDO',
    'CONCLUIDO',
    'RECUSADO'
);

CREATE TYPE public.tipo_agendamento AS ENUM (
    'PRESENCIAL',
    'DOMICILIAR',
    'TELEMEDICINA'
);

CREATE TYPE public.tipo_bloqueio AS ENUM (
    'FERIAS',
    'FERIADO',
    'INTERVALO',
    'OUTRO'
);
