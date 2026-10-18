package br.com.sgsm.service;

import br.com.sgsm.client.AuthServiceClient;
import br.com.sgsm.dto.AutoCadastroPacienteRequest;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.dto.PacienteResponse;
import br.com.sgsm.exception.CadastroIndisponivelException;
import br.com.sgsm.exception.MuitasTentativasException;
import br.com.sgsm.exception.ServicoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCadastroPacienteServiceTest {

    private static final String IP = "10.0.0.1";
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Mock private PacienteService pacienteService;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valores;

    private AutoCadastroPacienteService service;
    private final UUID pacienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valores);
        lenient().when(valores.increment(AutoCadastroPacienteService.PREFIXO_IP + IP)).thenReturn(1L);
        service = new AutoCadastroPacienteService(pacienteService, authServiceClient, redis, RELOGIO, 5);
    }

    private static AutoCadastroPacienteRequest pedido() {
        return new AutoCadastroPacienteRequest(" Maria Souza ", "529.982.247-25", LocalDate.of(1990, 5, 1),
                " maria@teste.com ", "11999990000", true, "senhaForte1");
    }

    private void pacienteCriado() {
        var resposta = new PacienteResponse();
        resposta.setId(pacienteId);
        when(pacienteService.cadastrar(any())).thenReturn(resposta);
    }

    private static HttpClientErrorException erro(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), new HttpHeaders(),
                "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void cadastraPacienteECriaLoginComAtivacaoPorEmail() {
        pacienteCriado();

        var resposta = service.cadastrar(pedido(), IP);

        assertThat(resposta.email()).isEqualTo("maria@teste.com");
        assertThat(resposta.ativacaoPendente()).isTrue();
        var enviado = ArgumentCaptor.forClass(CadastrarPacienteRequest.class);
        verify(pacienteService).cadastrar(enviado.capture());
        assertThat(enviado.getValue().nome()).isEqualTo("Maria Souza");
        assertThat(enviado.getValue().consentimentoLgpd()).isTrue();
        assertThat(enviado.getValue().logradouro()).isNull();
        verify(authServiceClient).registrarPacienteComAtivacaoPorEmail("maria@teste.com", "senhaForte1", pacienteId);
        verify(redis).expire(AutoCadastroPacienteService.PREFIXO_IP + IP, AutoCadastroPacienteService.JANELA);
        verify(pacienteService, never()).removerAutoCadastroIncompleto(any());
    }

    @Test
    void cpfOuEmailJaExistenteRecebeAMesmaRespostaGenerica() {
        when(pacienteService.cpfOuEmailJaCadastrado("529.982.247-25", "maria@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(pedido(), IP))
                .isInstanceOf(CadastroIndisponivelException.class)
                .hasMessage(AutoCadastroPacienteService.MENSAGEM_DUPLICADO);
        verify(pacienteService, never()).cadastrar(any());
    }

    @Test
    void corridaNoInsertViraRespostaGenerica() {
        when(pacienteService.cadastrar(any())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.cadastrar(pedido(), IP)).isInstanceOf(CadastroIndisponivelException.class);
        verifyNoInteractions(authServiceClient);
    }

    @Test
    void loginRecusadoPorEmailEmUsoDesfazOPaciente() {
        pacienteCriado();
        doThrow(erro(HttpStatus.CONFLICT)).when(authServiceClient).registrarPacienteComAtivacaoPorEmail(any(), any(), any());

        assertThatThrownBy(() -> service.cadastrar(pedido(), IP)).isInstanceOf(CadastroIndisponivelException.class);
        verify(pacienteService).removerAutoCadastroIncompleto(pacienteId);
    }

    @Test
    void outraRecusaDoAuthDesfazOPacienteEDevolveErroDeValidacao() {
        pacienteCriado();
        doThrow(erro(HttpStatus.BAD_REQUEST)).when(authServiceClient).registrarPacienteComAtivacaoPorEmail(any(), any(), any());

        assertThatThrownBy(() -> service.cadastrar(pedido(), IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Não foi possível criar o acesso");
        verify(pacienteService).removerAutoCadastroIncompleto(pacienteId);
    }

    @Test
    void authForaDoArDesfazOPacienteEDevolve503() {
        pacienteCriado();
        doThrow(new ResourceAccessException("connection refused"))
                .when(authServiceClient).registrarPacienteComAtivacaoPorEmail(any(), any(), any());

        assertThatThrownBy(() -> service.cadastrar(pedido(), IP)).isInstanceOf(ServicoIndisponivelException.class);
        verify(pacienteService).removerAutoCadastroIncompleto(pacienteId);
    }

    @Test
    void limitePorIpBloqueiaO6oCadastroDaJanela() {
        when(valores.increment(AutoCadastroPacienteService.PREFIXO_IP + IP)).thenReturn(6L);

        assertThatThrownBy(() -> service.cadastrar(pedido(), IP)).isInstanceOf(MuitasTentativasException.class);
        verifyNoInteractions(pacienteService, authServiceClient);
    }

    @Test
    void semRedisRecusaEmVezDeCadastrarSemLimite() {
        when(valores.increment(any())).thenThrow(new QueryTimeoutException("redis fora"));

        assertThatThrownBy(() -> service.cadastrar(pedido(), null)).isInstanceOf(ServicoIndisponivelException.class);
        verifyNoInteractions(pacienteService);
    }

    @Test
    void validaNomeEmailSenhaENascimentoAntesDeGravar() {
        var nascimento = LocalDate.of(1990, 5, 1);
        var invalidos = new AutoCadastroPacienteRequest[]{
                new AutoCadastroPacienteRequest("Al", "52998224725", nascimento, "a@b.com", null, true, "senhaForte1"),
                new AutoCadastroPacienteRequest(null, "52998224725", nascimento, "a@b.com", null, true, "senhaForte1"),
                new AutoCadastroPacienteRequest("Maria", "52998224725", nascimento, "sem-arroba", null, true, "senhaForte1"),
                new AutoCadastroPacienteRequest("Maria", "52998224725", nascimento, null, null, true, "senhaForte1"),
                new AutoCadastroPacienteRequest("Maria", "52998224725", nascimento, "a@b.com", null, true, "curta"),
                new AutoCadastroPacienteRequest("Maria", "52998224725", nascimento, "a@b.com", null, true, null),
                new AutoCadastroPacienteRequest("Maria", "52998224725", LocalDate.of(2030, 1, 1), "a@b.com", null, true, "senhaForte1"),
                new AutoCadastroPacienteRequest("Maria", "52998224725", LocalDate.of(1800, 1, 1), "a@b.com", null, true, "senhaForte1"),
                new AutoCadastroPacienteRequest("Maria", "52998224725", null, "a@b.com", null, true, "senhaForte1"),
        };
        for (var r : invalidos) {
            assertThatThrownBy(() -> service.cadastrar(r, IP)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(pacienteService, authServiceClient);
        assertThat(pedido().toString()).doesNotContain("senhaForte1").doesNotContain("529");
    }
}
