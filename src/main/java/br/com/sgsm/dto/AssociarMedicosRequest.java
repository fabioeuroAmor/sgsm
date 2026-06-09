package br.com.sgsm.dto;

import java.util.List;
import java.util.UUID;

public record AssociarMedicosRequest(List<UUID> medicoIds) {}
