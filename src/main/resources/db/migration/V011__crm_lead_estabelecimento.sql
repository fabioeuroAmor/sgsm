-- =============================================================================
-- V011 - Correção de segurança do sgsm-ia: lead por estabelecimento
-- Descrição: crm.lead não tinha vínculo com estabelecimento, então GET /crm/leads
--            devolvia os leads de todas as clínicas para qualquer usuário. A coluna
--            nova permite ao sgsm-ia filtrar leads pelos estabelecimentos permitidos
--            do usuário (EscopoAcessoService).
--            Nullable de propósito: leads antigos ficam sem estabelecimento e só
--            aparecem para DESENVOLVEDOR até serem atribuídos (decisão D3 do
--            Plano_Correcao_Seguranca_sgsm_ia_SGSM.docx). Leads novos criados por
--            FUNCIONARIO/ADMIN_ESTABELECIMENTO sempre informam o estabelecimento.
-- =============================================================================

ALTER TABLE crm.lead
    ADD COLUMN IF NOT EXISTS estabelecimento_id UUID REFERENCES sgsm.estabelecimento(id);

CREATE INDEX IF NOT EXISTS idx_crm_lead_estabelecimento ON crm.lead(estabelecimento_id);
