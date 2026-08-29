-- =============================================================================
-- V009 - LGPD formal em Paciente (item 3 do plano de compliance)
-- Descrição:
--   consentimento_lgpd_em — timestamp do consentimento explícito no cadastro
--   (obrigatório a partir de agora; nulo em pacientes cadastrados antes desta
--   migration, pois o consentimento não pode ser retroativo).
--
--   anonimizado — distingue "inativo recuperável" (ativo=false, anonimizado=false,
--   dado intacto) de "anonimizado irreversível" (ativo=false, anonimizado=true,
--   dados pessoais zerados). reativar() passa a bloquear quando anonimizado=true.
--
--   encerrado_em — data em que o cadastro foi inativado (setado por remover()),
--   usado como base de contagem pela rotina de expurgo (retenção de 20 anos após
--   o encerramento, conforme POLITICA_RETENCAO_DADOS.md). Nulo enquanto ativo.
-- =============================================================================

ALTER TABLE sgsm.paciente ADD COLUMN consentimento_lgpd_em TIMESTAMPTZ;
ALTER TABLE sgsm.paciente ADD COLUMN anonimizado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE sgsm.paciente ADD COLUMN encerrado_em TIMESTAMPTZ;
