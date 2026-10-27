package br.com.sgsm.controller;

import br.com.sgsm.dto.ConsentimentoRequest;
import br.com.sgsm.dto.VinculoPacienteClinicaRequest;
import br.com.sgsm.dto.WhatsappCanalInternoResponse;
import br.com.sgsm.dto.WhatsappCanalResponse;
import br.com.sgsm.service.PacienteClinicaService;
import br.com.sgsm.service.WhatsappCanalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappControllersTest {

    @Mock private WhatsappCanalService canalService;
    @Mock private PacienteClinicaService pacienteClinicaService;

    private final UUID clinica = UUID.randomUUID();

    @Test
    void telaDoAdminConectaConsultaEDesconecta() {
        var controller = new WhatsappCanalController(canalService);
        var resposta = new WhatsappCanalResponse(UUID.randomUUID(), clinica, "EVOLUTION", "PENDENTE_QR", null, null, "QR");
        when(canalService.conectar(clinica)).thenReturn(resposta);
        when(canalService.consultar(clinica)).thenReturn(resposta);

        assertThat(controller.conectar(clinica).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.consultar(clinica).getBody()).isEqualTo(resposta);
        assertThat(controller.desconectar(clinica).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(canalService).desconectar(clinica);
    }

    @Test
    void endpointsInternosDoBot() {
        var controller = new WhatsappInternoController(canalService, pacienteClinicaService);
        var canal = new WhatsappCanalInternoResponse("sgsm-abc", clinica, "Clinica", "CONECTADO");
        when(canalService.resolver("sgsm-abc")).thenReturn(canal);
        when(canalService.listarConectados()).thenReturn(List.of(canal));
        when(pacienteClinicaService.consentimentoVigente("5561", clinica, "WHATSAPP")).thenReturn(true);
        var vinculo = new VinculoPacienteClinicaRequest(UUID.randomUUID(), clinica, "WHATSAPP", null);
        var consentimento = new ConsentimentoRequest("5561", null, clinica, "WHATSAPP", "v1");

        assertThat(controller.canal("sgsm-abc").getBody()).isEqualTo(canal);
        assertThat(controller.conectados().getBody()).containsExactly(canal);
        assertThat(controller.vincular(vinculo).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.consentir(consentimento).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.consentimentoVigente("5561", clinica, "WHATSAPP").getBody()).containsEntry("vigente", true);
        verify(pacienteClinicaService).vincular(vinculo);
        verify(pacienteClinicaService).registrarConsentimento(consentimento);
    }
}
