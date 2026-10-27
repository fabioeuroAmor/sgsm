-- =============================================================================
-- V015 - Vínculo explícito paciente x clínica (DW5)
-- Descrição: até aqui a equipe da clínica só enxergava o paciente depois do primeiro
--            agendamento (escopo via Agendamento.medicoId). O vínculo registra por
--            onde o paciente chegou a cada clínica — WhatsApp da clínica, site ou
--            cadastro pela própria clínica — e entra no escopo da secretária/admin.
--            O paciente continua único (CPF); cada clínica vê só os seus vínculos.
-- =============================================================================

CREATE TABLE IF NOT EXISTS sgsm.paciente_estabelecimento (
    paciente_id         UUID        NOT NULL REFERENCES sgsm.paciente(id),
    estabelecimento_id  UUID        NOT NULL REFERENCES sgsm.estabelecimento(id),
    origem              VARCHAR(20) NOT NULL CHECK (origem IN ('WHATSAPP', 'SITE', 'CLINICA')),
    primeiro_contato_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (paciente_id, estabelecimento_id)
);

CREATE INDEX IF NOT EXISTS idx_paciente_estabelecimento_estab
    ON sgsm.paciente_estabelecimento (estabelecimento_id);
