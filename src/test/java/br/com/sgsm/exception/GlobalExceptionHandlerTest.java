package br.com.sgsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Testa o mapeamento de exceções de negócio para respostas RFC 7807 (ProblemDetail),
 * conforme convenção do projeto (GlobalExceptionHandler + exceções sem hierarquia customizada).
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void deveRetornar404QuandoRecursoNaoEncontrado() {
        when(request.getRequestURI()).thenReturn("/v1/api/medicos/123");
        var ex = new RecursoNaoEncontradoException("Médico não encontrado: 123");

        ResponseEntity<ProblemDetail> response = handler.handleNaoEncontrado(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Médico não encontrado: 123");
        assertThat(response.getBody().getTitle()).isEqualTo("Recurso não encontrado");
        assertThat(response.getBody().getType().toString()).isEqualTo("https://sgsm.com.br/erros/recurso-nao-encontrado");
        assertThat(response.getBody().getInstance().toString()).isEqualTo("/v1/api/medicos/123");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void deveRetornar403QuandoAcessoNegado() {
        when(request.getRequestURI()).thenReturn("/v1/api/agendamentos/123");
        var ex = new AcessoNegadoException("Acesso negado ao agendamento: 123");

        ResponseEntity<ProblemDetail> response = handler.handleAcessoNegado(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getDetail()).isEqualTo("Acesso negado ao agendamento: 123");
        assertThat(response.getBody().getTitle()).isEqualTo("Acesso negado");
        assertThat(response.getBody().getType().toString()).isEqualTo("https://sgsm.com.br/erros/acesso-negado");
    }

    @Test
    void deveRetornar400QuandoArgumentoInvalido() {
        when(request.getRequestURI()).thenReturn("/v1/api/medicos");
        var ex = new IllegalArgumentException("CRM já cadastrado");

        ResponseEntity<ProblemDetail> response = handler.handleArgumentoInvalido(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("CRM já cadastrado");
        assertThat(response.getBody().getTitle()).isEqualTo("Requisição inválida");
        assertThat(response.getBody().getType().toString()).isEqualTo("https://sgsm.com.br/erros/argumento-invalido");
    }

    @Test
    void deveRetornar400QuandoViolacaoDeIntegridadeDeDados() {
        when(request.getRequestURI()).thenReturn("/v1/api/pacientes");
        var ex = new DataIntegrityViolationException("duplicate key value violates unique constraint");

        ResponseEntity<ProblemDetail> response = handler.handleIntegridadeDados(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("Dado já cadastrado");
        assertThat(response.getBody().getType().toString()).isEqualTo("https://sgsm.com.br/erros/dado-duplicado");
    }

    @Test
    void deveRetornar500ComMensagemGenericaQuandoErroInterno() {
        when(request.getRequestURI()).thenReturn("/v1/api/medicos");
        var ex = new RuntimeException("NullPointerException interna qualquer");

        ResponseEntity<ProblemDetail> response = handler.handleErroInterno(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("Erro interno. Tente novamente mais tarde.");
        assertThat(response.getBody().getTitle()).isEqualTo("Erro interno");
        assertThat(response.getBody().getType().toString()).isEqualTo("https://sgsm.com.br/erros/erro-interno");
    }
}
