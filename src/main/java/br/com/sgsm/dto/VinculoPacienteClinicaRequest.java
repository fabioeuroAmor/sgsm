package br.com.sgsm.dto;

import java.util.UUID;

// origem: WHATSAPP | SITE | CLINICA. telefone (opcional, WhatsApp): o consentimento dado antes do
// cadastro com esse telefone passa a apontar para o paciente.
public record VinculoPacienteClinicaRequest(UUID pacienteId, UUID estabelecimentoId, String origem, String telefone) {}
