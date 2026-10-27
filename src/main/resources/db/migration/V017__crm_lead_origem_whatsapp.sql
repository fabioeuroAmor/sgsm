-- =============================================================================
-- V017 - Lead vindo do WhatsApp da clínica
-- Descrição: o primeiro contato de um número desconhecido pelo WhatsApp de uma
--            clínica vira lead no CRM daquela clínica (Fase 4 do plano de WhatsApp
--            por clínica); o lead passa a CONVERTIDO quando a pessoa se cadastra ou
--            entra na conta pelo próprio WhatsApp.
-- =============================================================================

ALTER TYPE crm.origem_lead ADD VALUE IF NOT EXISTS 'WHATSAPP';
