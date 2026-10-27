package br.com.sgsm.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// qrCodeBase64 só vem enquanto o número não foi conectado (status PENDENTE_QR ou DESCONECTADO)
public record WhatsappCanalResponse(
        UUID id,
        UUID estabelecimentoId,
        String provedor,
        String status,
        String numero,
        OffsetDateTime conectadoEm,
        String qrCodeBase64
) {}
