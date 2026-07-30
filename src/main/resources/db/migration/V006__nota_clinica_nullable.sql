-- Torna agendamento_id e medico_id opcionais em nota_clinica.
-- Notas clínicas podem ser registradas por qualquer usuário do sistema,
-- não necessariamente vinculadas a um agendamento ou médico específico.
ALTER TABLE crm.nota_clinica ALTER COLUMN agendamento_id DROP NOT NULL;
ALTER TABLE crm.nota_clinica ALTER COLUMN medico_id DROP NOT NULL;
