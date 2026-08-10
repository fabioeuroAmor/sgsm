-- Adiciona LEAD ao enum crm.tipo_documento para permitir indexação de leads no Milvus
ALTER TYPE crm.tipo_documento ADD VALUE IF NOT EXISTS 'LEAD';
