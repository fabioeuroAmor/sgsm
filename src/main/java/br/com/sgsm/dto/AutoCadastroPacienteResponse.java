package br.com.sgsm.dto;

// Não devolve o id do paciente: com ele (e o e-mail) seria possível criar o login no
// /auth/registrar sem passar pela ativação por e-mail
public record AutoCadastroPacienteResponse(String email, boolean ativacaoPendente) {}
