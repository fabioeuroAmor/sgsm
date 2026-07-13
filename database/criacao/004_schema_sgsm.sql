-- =============================================================================
-- SGSM - Script de criação do schema sgsm (espelho do public)
-- Gerado em: 2026-07-06 21:16
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS sgsm;

SET search_path TO sgsm;
-- PostgreSQL database dump


-- Dumped from database version 18.0
-- Dumped by pg_dump version 18.0

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

-- Name: public; Type: SCHEMA; Schema: -; Owner: -



-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -



-- Name: dia_semana; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.dia_semana AS ENUM (
    'SEGUNDA',
    'TERCA',
    'QUARTA',
    'QUINTA',
    'SEXTA',
    'SABADO',
    'DOMINGO'
);


-- Name: metodo_pagamento; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.metodo_pagamento AS ENUM (
    'PIX',
    'CARTAO_CREDITO'
);


-- Name: origem_cancelamento; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.origem_cancelamento AS ENUM (
    'PACIENTE',
    'MEDICO',
    'ESTABELECIMENTO',
    'SISTEMA'
);


-- Name: status_agendamento; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.status_agendamento AS ENUM (
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


-- Name: status_pagamento; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.status_pagamento AS ENUM (
    'PENDENTE',
    'PROCESSANDO',
    'APROVADO',
    'RECUSADO',
    'CANCELADO',
    'REEMBOLSADO'
);


-- Name: status_reembolso; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.status_reembolso AS ENUM (
    'SOLICITADO',
    'PROCESSANDO',
    'CONCLUIDO',
    'RECUSADO'
);


-- Name: tipo_agendamento; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.tipo_agendamento AS ENUM (
    'PRESENCIAL',
    'DOMICILIAR',
    'TELEMEDICINA'
);


-- Name: tipo_bloqueio; Type: TYPE; Schema: public; Owner: -

CREATE TYPE sgsm.tipo_bloqueio AS ENUM (
    'FERIAS',
    'FERIADO',
    'INTERVALO',
    'OUTRO'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

-- Name: estabelecimento; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.estabelecimento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nome character varying(200) NOT NULL,
    cnpj character varying(18) NOT NULL,
    telefone character varying(20),
    email character varying(255),
    logradouro character varying(200) NOT NULL,
    numero character varying(10) NOT NULL,
    complemento character varying(100),
    bairro character varying(100) NOT NULL,
    cidade character varying(100) NOT NULL,
    uf character(2) NOT NULL,
    cep character varying(9) NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);


-- Name: medico; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.medico (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nome character varying(150) NOT NULL,
    crm character varying(20) NOT NULL,
    crm_uf character(2) NOT NULL,
    especialidade character varying(100) NOT NULL,
    email character varying(255) NOT NULL,
    telefone character varying(20),
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);


-- Name: paciente; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.paciente (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nome character varying(150) NOT NULL,
    cpf character varying(14) NOT NULL,
    data_nascimento date NOT NULL,
    email character varying(255) NOT NULL,
    telefone character varying(20),
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    logradouro character varying(255),
    numero character varying(20),
    complemento character varying(100),
    bairro character varying(100),
    cidade character varying(100),
    uf character(2),
    cep character varying(9)
);


-- Name: agendamento; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.agendamento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    paciente_id uuid NOT NULL,
    servico_medico_id uuid NOT NULL,
    medico_id uuid NOT NULL,
    estabelecimento_id uuid,
    pagamento_id uuid,
    data_hora_inicio timestamp with time zone NOT NULL,
    data_hora_fim timestamp with time zone NOT NULL,
    status sgsm.status_agendamento DEFAULT 'PENDENTE'::sgsm.status_agendamento NOT NULL,
    observacoes text,
    origem_cancelamento sgsm.origem_cancelamento,
    motivo_cancelamento character varying(500),
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    tipo sgsm.tipo_agendamento DEFAULT 'PRESENCIAL'::sgsm.tipo_agendamento NOT NULL,
    localizacao_medico text,
    CONSTRAINT chk_agendamento_periodo CHECK ((data_hora_fim > data_hora_inicio))
);


-- Name: pagamento; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.pagamento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    servico_medico_id uuid NOT NULL,
    valor numeric(10,2) NOT NULL,
    metodo sgsm.metodo_pagamento NOT NULL,
    status sgsm.status_pagamento DEFAULT 'PENDENTE'::sgsm.status_pagamento NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    paciente_id uuid,
    CONSTRAINT pagamento_valor_check CHECK ((valor > (0)::numeric))
);


-- Name: reembolso; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.reembolso (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pagamento_id uuid NOT NULL,
    motivo text NOT NULL,
    valor numeric(10,2) NOT NULL,
    status sgsm.status_reembolso DEFAULT 'SOLICITADO'::sgsm.status_reembolso NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reembolso_valor_check CHECK ((valor > (0)::numeric))
);


-- Name: agenda_medico; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.agenda_medico (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    medico_id uuid NOT NULL,
    estabelecimento_id uuid,
    dia_semana sgsm.dia_semana NOT NULL,
    hora_inicio time without time zone NOT NULL,
    hora_fim time without time zone NOT NULL,
    duracao_slot_minutos integer DEFAULT 30 NOT NULL,
    data_vigencia_inicio date NOT NULL,
    data_vigencia_fim date,
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    domiciliar boolean DEFAULT false NOT NULL,
    raio_km numeric(6,2),
    cidade_atendimento character varying(100),
    uf_atendimento character(2),
    intervalo_deslocamento_minutos integer,
    CONSTRAINT agenda_medico_duracao_slot_minutos_check CHECK ((duracao_slot_minutos > 0)),
    CONSTRAINT chk_agenda_horario CHECK ((hora_fim > hora_inicio))
);


-- Name: bloqueio_agenda; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.bloqueio_agenda (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    medico_id uuid NOT NULL,
    estabelecimento_id uuid,
    tipo sgsm.tipo_bloqueio DEFAULT 'OUTRO'::sgsm.tipo_bloqueio NOT NULL,
    data_hora_inicio timestamp with time zone NOT NULL,
    data_hora_fim timestamp with time zone NOT NULL,
    motivo character varying(300),
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_bloqueio_periodo CHECK ((data_hora_fim > data_hora_inicio))
);


-- Name: historico_status_agendamento; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.historico_status_agendamento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    agendamento_id uuid NOT NULL,
    status_anterior sgsm.status_agendamento,
    status_novo sgsm.status_agendamento NOT NULL,
    observacao character varying(500),
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);


-- Name: historico_status_pagamento; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.historico_status_pagamento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pagamento_id uuid NOT NULL,
    status_anterior sgsm.status_pagamento,
    status_novo sgsm.status_pagamento NOT NULL,
    observacao text,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);


-- Name: medico_estabelecimento; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.medico_estabelecimento (
    medico_id uuid NOT NULL,
    estabelecimento_id uuid NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);


-- Name: pagamento_cartao; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.pagamento_cartao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pagamento_id uuid NOT NULL,
    numero_mascarado character varying(19) NOT NULL,
    bandeira character varying(50) NOT NULL,
    parcelas smallint DEFAULT 1 NOT NULL,
    codigo_autorizacao character varying(100),
    nsu character varying(50),
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pagamento_cartao_parcelas_check CHECK (((parcelas >= 1) AND (parcelas <= 12)))
);


-- Name: pagamento_pix; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.pagamento_pix (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pagamento_id uuid NOT NULL,
    txid character varying(35) NOT NULL,
    chave_pix character varying(150),
    qr_code text,
    codigo_copia_cola text,
    data_expiracao timestamp with time zone,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);


-- Name: servico_medico; Type: TABLE; Schema: public; Owner: -

CREATE TABLE sgsm.servico_medico (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    medico_id uuid NOT NULL,
    nome character varying(150) NOT NULL,
    descricao text,
    preco numeric(10,2) NOT NULL,
    duracao_minutos integer,
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    domiciliar boolean DEFAULT false NOT NULL,
    taxa_deslocamento numeric(10,2),
    CONSTRAINT servico_medico_duracao_minutos_check CHECK ((duracao_minutos > 0)),
    CONSTRAINT servico_medico_preco_check CHECK ((preco >= (0)::numeric))
);


-- Name: agenda_medico agenda_medico_medico_id_estabelecimento_id_dia_semana_hora__key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agenda_medico
    ADD CONSTRAINT agenda_medico_medico_id_estabelecimento_id_dia_semana_hora__key UNIQUE (medico_id, estabelecimento_id, dia_semana, hora_inicio);


-- Name: agenda_medico agenda_medico_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agenda_medico
    ADD CONSTRAINT agenda_medico_pkey PRIMARY KEY (id);


-- Name: agendamento agendamento_pagamento_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agendamento
    ADD CONSTRAINT agendamento_pagamento_id_key UNIQUE (pagamento_id);


-- Name: agendamento agendamento_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agendamento
    ADD CONSTRAINT agendamento_pkey PRIMARY KEY (id);


-- Name: bloqueio_agenda bloqueio_agenda_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.bloqueio_agenda
    ADD CONSTRAINT bloqueio_agenda_pkey PRIMARY KEY (id);


-- Name: estabelecimento estabelecimento_cnpj_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.estabelecimento
    ADD CONSTRAINT estabelecimento_cnpj_key UNIQUE (cnpj);


-- Name: estabelecimento estabelecimento_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.estabelecimento
    ADD CONSTRAINT estabelecimento_pkey PRIMARY KEY (id);


-- Name: historico_status_agendamento historico_status_agendamento_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.historico_status_agendamento
    ADD CONSTRAINT historico_status_agendamento_pkey PRIMARY KEY (id);


-- Name: historico_status_pagamento historico_status_pagamento_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.historico_status_pagamento
    ADD CONSTRAINT historico_status_pagamento_pkey PRIMARY KEY (id);


-- Name: medico medico_crm_crm_uf_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.medico
    ADD CONSTRAINT medico_crm_crm_uf_key UNIQUE (crm, crm_uf);


-- Name: medico medico_email_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.medico
    ADD CONSTRAINT medico_email_key UNIQUE (email);


-- Name: medico_estabelecimento medico_estabelecimento_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.medico_estabelecimento
    ADD CONSTRAINT medico_estabelecimento_pkey PRIMARY KEY (medico_id, estabelecimento_id);


-- Name: medico medico_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.medico
    ADD CONSTRAINT medico_pkey PRIMARY KEY (id);


-- Name: paciente paciente_cpf_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.paciente
    ADD CONSTRAINT paciente_cpf_key UNIQUE (cpf);


-- Name: paciente paciente_email_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.paciente
    ADD CONSTRAINT paciente_email_key UNIQUE (email);


-- Name: paciente paciente_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.paciente
    ADD CONSTRAINT paciente_pkey PRIMARY KEY (id);


-- Name: pagamento_cartao pagamento_cartao_pagamento_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento_cartao
    ADD CONSTRAINT pagamento_cartao_pagamento_id_key UNIQUE (pagamento_id);


-- Name: pagamento_cartao pagamento_cartao_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento_cartao
    ADD CONSTRAINT pagamento_cartao_pkey PRIMARY KEY (id);


-- Name: pagamento_pix pagamento_pix_pagamento_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento_pix
    ADD CONSTRAINT pagamento_pix_pagamento_id_key UNIQUE (pagamento_id);


-- Name: pagamento_pix pagamento_pix_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento_pix
    ADD CONSTRAINT pagamento_pix_pkey PRIMARY KEY (id);


-- Name: pagamento_pix pagamento_pix_txid_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento_pix
    ADD CONSTRAINT pagamento_pix_txid_key UNIQUE (txid);


-- Name: pagamento pagamento_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento
    ADD CONSTRAINT pagamento_pkey PRIMARY KEY (id);


-- Name: reembolso reembolso_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.reembolso
    ADD CONSTRAINT reembolso_pkey PRIMARY KEY (id);


-- Name: servico_medico servico_medico_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.servico_medico
    ADD CONSTRAINT servico_medico_pkey PRIMARY KEY (id);


-- Name: agenda_medico agenda_medico_estabelecimento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agenda_medico
    ADD CONSTRAINT agenda_medico_estabelecimento_id_fkey FOREIGN KEY (estabelecimento_id) REFERENCES sgsm.estabelecimento(id);


-- Name: agenda_medico agenda_medico_medico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agenda_medico
    ADD CONSTRAINT agenda_medico_medico_id_fkey FOREIGN KEY (medico_id) REFERENCES sgsm.medico(id);


-- Name: agendamento agendamento_estabelecimento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agendamento
    ADD CONSTRAINT agendamento_estabelecimento_id_fkey FOREIGN KEY (estabelecimento_id) REFERENCES sgsm.estabelecimento(id);


-- Name: agendamento agendamento_medico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agendamento
    ADD CONSTRAINT agendamento_medico_id_fkey FOREIGN KEY (medico_id) REFERENCES sgsm.medico(id);


-- Name: agendamento agendamento_paciente_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agendamento
    ADD CONSTRAINT agendamento_paciente_id_fkey FOREIGN KEY (paciente_id) REFERENCES sgsm.paciente(id);


-- Name: agendamento agendamento_pagamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agendamento
    ADD CONSTRAINT agendamento_pagamento_id_fkey FOREIGN KEY (pagamento_id) REFERENCES sgsm.pagamento(id);


-- Name: agendamento agendamento_servico_medico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.agendamento
    ADD CONSTRAINT agendamento_servico_medico_id_fkey FOREIGN KEY (servico_medico_id) REFERENCES sgsm.servico_medico(id);


-- Name: bloqueio_agenda bloqueio_agenda_estabelecimento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.bloqueio_agenda
    ADD CONSTRAINT bloqueio_agenda_estabelecimento_id_fkey FOREIGN KEY (estabelecimento_id) REFERENCES sgsm.estabelecimento(id);


-- Name: bloqueio_agenda bloqueio_agenda_medico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.bloqueio_agenda
    ADD CONSTRAINT bloqueio_agenda_medico_id_fkey FOREIGN KEY (medico_id) REFERENCES sgsm.medico(id);


-- Name: historico_status_agendamento historico_status_agendamento_agendamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.historico_status_agendamento
    ADD CONSTRAINT historico_status_agendamento_agendamento_id_fkey FOREIGN KEY (agendamento_id) REFERENCES sgsm.agendamento(id);


-- Name: historico_status_pagamento historico_status_pagamento_pagamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.historico_status_pagamento
    ADD CONSTRAINT historico_status_pagamento_pagamento_id_fkey FOREIGN KEY (pagamento_id) REFERENCES sgsm.pagamento(id);


-- Name: medico_estabelecimento medico_estabelecimento_estabelecimento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.medico_estabelecimento
    ADD CONSTRAINT medico_estabelecimento_estabelecimento_id_fkey FOREIGN KEY (estabelecimento_id) REFERENCES sgsm.estabelecimento(id);


-- Name: medico_estabelecimento medico_estabelecimento_medico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.medico_estabelecimento
    ADD CONSTRAINT medico_estabelecimento_medico_id_fkey FOREIGN KEY (medico_id) REFERENCES sgsm.medico(id);


-- Name: pagamento_cartao pagamento_cartao_pagamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento_cartao
    ADD CONSTRAINT pagamento_cartao_pagamento_id_fkey FOREIGN KEY (pagamento_id) REFERENCES sgsm.pagamento(id);


-- Name: pagamento pagamento_paciente_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento
    ADD CONSTRAINT pagamento_paciente_id_fkey FOREIGN KEY (paciente_id) REFERENCES sgsm.paciente(id);


-- Name: pagamento_pix pagamento_pix_pagamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento_pix
    ADD CONSTRAINT pagamento_pix_pagamento_id_fkey FOREIGN KEY (pagamento_id) REFERENCES sgsm.pagamento(id);


-- Name: pagamento pagamento_servico_medico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.pagamento
    ADD CONSTRAINT pagamento_servico_medico_id_fkey FOREIGN KEY (servico_medico_id) REFERENCES sgsm.servico_medico(id);


-- Name: reembolso reembolso_pagamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.reembolso
    ADD CONSTRAINT reembolso_pagamento_id_fkey FOREIGN KEY (pagamento_id) REFERENCES sgsm.pagamento(id);


-- Name: servico_medico servico_medico_medico_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY sgsm.servico_medico
    ADD CONSTRAINT servico_medico_medico_id_fkey FOREIGN KEY (medico_id) REFERENCES sgsm.medico(id);


-- PostgreSQL database dump complete


