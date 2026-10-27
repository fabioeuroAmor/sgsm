package br.com.sgsm.dto;

import java.util.UUID;

// Resposta para o ms-whatsapp-bot: de qual clínica é a instância que recebeu a mensagem
public record WhatsappCanalInternoResponse(
        String instancia,
        UUID estabelecimentoId,
        String estabelecimentoNome,
        String status
) {}
