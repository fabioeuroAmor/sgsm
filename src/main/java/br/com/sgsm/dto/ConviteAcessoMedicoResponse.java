package br.com.sgsm.dto;

// loginCriado=false: o médico já tinha login e recebeu um novo link para definir a senha
public record ConviteAcessoMedicoResponse(String email, boolean loginCriado) {}
