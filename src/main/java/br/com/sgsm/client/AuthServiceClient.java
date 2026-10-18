package br.com.sgsm.client;

import br.com.sgsm.dto.ConviteAcessoMedicoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

// Chamada servidor a servidor ao ms-sboot-auth no auto-cadastro público de paciente: o sgsm cria o
// login logo após o paciente, sem o id do paciente passar pelo navegador
@Component
public class AuthServiceClient {

    private final RestClient restClient;

    @Autowired
    public AuthServiceClient(@Value("${sgsm.auth.base-url:http://localhost:8081}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl));
    }

    AuthServiceClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    // confirmarEmail=true: a conta nasce inativa e o auth envia o código de ativação ao e-mail
    public void registrarPacienteComAtivacaoPorEmail(String email, String senha, UUID pacienteId) {
        restClient.post()
                .uri("/v1/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "senha", senha,
                        "tipoPerfil", "PACIENTE",
                        "referenciaId", pacienteId.toString(),
                        "confirmarEmail", true))
                .retrieve()
                .toBodilessEntity();
    }

    // Repassa o token de quem convida: o auth exige um chamador da equipe
    public ConviteAcessoMedicoResponse enviarConviteMedico(UUID medicoId, String authorization) {
        return restClient.post()
                .uri("/v1/api/auth/convite-medico")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("medicoId", medicoId.toString()))
                .retrieve()
                .body(ConviteAcessoMedicoResponse.class);
    }
}
