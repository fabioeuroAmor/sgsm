-- =============================================================================
-- V014 - WhatsApp por clínica: canal (número) de cada estabelecimento
-- Descrição: cada clínica conecta o próprio número ao SGSM (Plano_Desenvolvimento_
--            WhatsApp_Por_Clinica_SGSM.docx, DW1-DW3). A clínica de uma mensagem
--            vem SEMPRE da instância que a recebeu, nunca do texto da conversa.
--            instancia = nome da instância na Evolution API (piloto) ou
--            phone_number_id na WhatsApp Cloud API (produção, Fase 7).
--            Um canal ativo por clínica (DW3); canais removidos ficam no histórico.
-- =============================================================================

CREATE TABLE IF NOT EXISTS sgsm.whatsapp_canal (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID         NOT NULL REFERENCES sgsm.estabelecimento(id),
    provedor           VARCHAR(20)  NOT NULL DEFAULT 'EVOLUTION'
                       CHECK (provedor IN ('EVOLUTION', 'CLOUD_API')),
    instancia          VARCHAR(100) NOT NULL UNIQUE,
    numero_e164        VARCHAR(20),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE_QR'
                       CHECK (status IN ('PENDENTE_QR', 'CONECTADO', 'DESCONECTADO', 'REMOVIDO')),
    conectado_em       TIMESTAMPTZ,
    criado_por         UUID,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_whatsapp_canal_clinica_ativo
    ON sgsm.whatsapp_canal (estabelecimento_id) WHERE status <> 'REMOVIDO';
