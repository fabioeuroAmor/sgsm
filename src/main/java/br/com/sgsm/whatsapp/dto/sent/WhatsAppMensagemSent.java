package br.com.sgsm.whatsapp.dto.sent;

// Corpo do POST /message/sendText/{instance} da Evolution API.
// ATENCAO: confirmar o nome exato dos campos contra a instancia real quando configurada.
public record WhatsAppMensagemSent(String number, String text) {}
