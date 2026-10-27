-- O sgsm-ia indexa serviços com tipo 'SERVICO_MEDICO' (DocumentoBuilder, EtlSyncService, filtro do RAG),
-- mas a V004 criou o enum só com 'SERVICO': nenhum serviço médico chegava ao crm.documento/Milvus e a IA
-- não achava o servicoMedicoId para agendar. 'SERVICO' fica (enum do Postgres não remove valor).
ALTER TYPE crm.tipo_documento ADD VALUE IF NOT EXISTS 'SERVICO_MEDICO';
