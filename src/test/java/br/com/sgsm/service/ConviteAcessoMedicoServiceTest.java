package br.com.sgsm.service;

import br.com.sgsm.client.AuthServiceClient;
import br.com.sgsm.domain.Medico;
import br.com.sgsm.dto.ConviteAcessoMedicoResponse;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.exception.ServicoIndisponivelException;
import br.com.sgsm.repository.MedicoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConviteAcessoMedicoServiceTest {

    private static final String BEARER = "Bearer jwt";

    @Mock private MedicoRepository medicoRepository;
    @Mock private EstabelecimentoAcessoService acesso;
    @Mock private ContextoSeguranca contexto;
    @Mock private AuthServiceClient authServiceClient;

    private ConviteAcessoMedicoService service;
    private final UUID medicoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ConviteAcessoMedicoService(medicoRepository, acesso, contexto, authServiceClient);
    }

    private void medicoExiste(boolean ativo) {
        var m = new Medico();
        m.setAtivo(ativo);
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(m));
    }

    private static HttpClientErrorException erro(HttpStatus status, String corpo) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), new HttpHeaders(),
                corpo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void desenvolvedorConvidaQualquerMedico() {
        medicoExiste(true);
        var resposta = new ConviteAcessoMedicoResponse("dr@a.com", true);
        when(authServiceClient.enviarConviteMedico(medicoId, BEARER)).thenReturn(resposta);

        assertThat(service.convidar(medicoId, BEARER)).isEqualTo(resposta);
        verifyNoInteractions(acesso);
    }

    @Test
    void secretariaConvidaMedicoDaClinicaDela() {
        medicoExiste(true);
        when(contexto.isFuncionario()).thenReturn(true);
        when(acesso.medicoPermitido(medicoId)).thenReturn(true);
        when(authServiceClient.enviarConviteMedico(medicoId, BEARER)).thenReturn(new ConviteAcessoMedicoResponse("dr@a.com", false));

        service.convidar(medicoId, BEARER);

        verify(authServiceClient).enviarConviteMedico(medicoId, BEARER);
    }

    // Médico de outra clínica OU sem clínica nenhuma (QA125): 404, nunca convite
    @Test
    void secretariaNaoConvidaMedicoForaDasSuasClinicas() {
        medicoExiste(true);
        when(contexto.isAdminEstabelecimento()).thenReturn(true);
        when(acesso.medicoPermitido(medicoId)).thenReturn(false);

        assertThatThrownBy(() -> service.convidar(medicoId, BEARER)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(authServiceClient);
    }

    @Test
    void medicoInexistenteOuInativoNaoRecebeConvite() {
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.convidar(medicoId, BEARER)).isInstanceOf(RecursoNaoEncontradoException.class);

        medicoExiste(false);
        assertThatThrownBy(() -> service.convidar(medicoId, BEARER)).hasMessageContaining("inativo");
        verifyNoInteractions(authServiceClient);
    }

    @Test
    void recusaDoAuthVoltaComAMensagemDele() {
        medicoExiste(true);
        when(authServiceClient.enviarConviteMedico(any(), any()))
                .thenThrow(erro(HttpStatus.CONFLICT, "{\"title\":\"x\",\"detail\":\"O e-mail deste médico já é usado por outro acesso no sistema.\"}"))
                .thenThrow(erro(HttpStatus.BAD_REQUEST, "sem json"));

        assertThatThrownBy(() -> service.convidar(medicoId, BEARER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O e-mail deste médico já é usado por outro acesso no sistema.");
        assertThatThrownBy(() -> service.convidar(medicoId, BEARER))
                .hasMessage("Não foi possível enviar o convite de acesso.");
    }

    @Test
    void authForaDoArViraIndisponivel() {
        medicoExiste(true);
        when(authServiceClient.enviarConviteMedico(any(), any())).thenThrow(new ResourceAccessException("down"));

        assertThatThrownBy(() -> service.convidar(medicoId, BEARER)).isInstanceOf(ServicoIndisponivelException.class);
    }
}
