-- =============================================================================
-- V016 - Consentimento LGPD por clínica e canal (DW6)
-- Descrição: o paciente consente com o tratamento dos dados POR AQUELA CLÍNICA e
--            canal; outra clínica pede de novo. No WhatsApp o aceite acontece antes
--            do cadastro, então o registro guarda o telefone e ganha o paciente_id
--            quando ele se identifica. Revogação mantém o histórico (revogado_em).
-- =============================================================================

CREATE TABLE IF NOT EXISTS sgsm.consentimento_lgpd (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id        UUID         REFERENCES sgsm.paciente(id),
    telefone           VARCHAR(20),
    estabelecimento_id UUID         NOT NULL REFERENCES sgsm.estabelecimento(id),
    canal              VARCHAR(20)  NOT NULL CHECK (canal IN ('WHATSAPP', 'SITE', 'CLINICA')),
    versao_texto       VARCHAR(40)  NOT NULL,
    aceito_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revogado_em        TIMESTAMPTZ,
    CONSTRAINT chk_consentimento_titular CHECK (paciente_id IS NOT NULL OR telefone IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_consentimento_telefone
    ON sgsm.consentimento_lgpd (telefone, estabelecimento_id, canal) WHERE revogado_em IS NULL;
CREATE INDEX IF NOT EXISTS idx_consentimento_paciente
    ON sgsm.consentimento_lgpd (paciente_id, estabelecimento_id) WHERE revogado_em IS NULL;
