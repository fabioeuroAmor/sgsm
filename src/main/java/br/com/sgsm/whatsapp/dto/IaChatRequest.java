package br.com.sgsm.whatsapp.dto;

// Espelha br.com.sgsm.ia.dto.ChatRequest do sgsm-ia — servicos separados, sem dependencia
// de codigo compartilhada, entao o contrato JSON e reproduzido aqui.
public record IaChatRequest(String pergunta) {}
