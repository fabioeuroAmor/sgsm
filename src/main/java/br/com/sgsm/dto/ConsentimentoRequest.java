package br.com.sgsm.dto;

import java.util.UUID;

// Aceite do termo LGPD de uma clínica num canal. No WhatsApp vem só o telefone (antes do cadastro).
public record ConsentimentoRequest(
        String telefone,
        UUID pacienteId,
        UUID estabelecimentoId,
        String canal,
        String versaoTexto
) {}
