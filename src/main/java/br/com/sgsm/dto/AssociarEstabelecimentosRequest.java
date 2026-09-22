package br.com.sgsm.dto;

import java.util.List;
import java.util.UUID;

public record AssociarEstabelecimentosRequest(List<UUID> estabelecimentoIds) {}
