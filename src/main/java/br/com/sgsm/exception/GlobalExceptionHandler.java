package br.com.sgsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;

// @Order garante prioridade sobre o ProblemDetail automático do Spring
// (spring.mvc.problemdetails.enabled=true) para MethodArgumentNotValidException,
// que senão intercepta a exceção antes deste advice e devolve um erro genérico
// ("Invalid request content."), sem a lista de campos inválidos.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Erros de @Valid nos DTOs de request (jakarta.validation) — mesma resposta RFC 7807
    // dos demais erros, com a lista de campos inválidos na extensão "errors".
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var erros = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList();

        var response = problem(HttpStatus.BAD_REQUEST, "validacao", "Requisição inválida",
                "Um ou mais campos são inválidos.", request);
        response.getBody().setProperty("errors", erros);
        return response;
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> handleNaoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "recurso-nao-encontrado", "Recurso não encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ProblemDetail> handleAcessoNegado(AcessoNegadoException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "acesso-negado", "Acesso negado", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleArgumentoInvalido(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "argumento-invalido", "Requisição inválida", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleErroInterno(Exception ex, HttpServletRequest request) {
        log.error("Erro interno não tratado em {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro interno",
                "Erro interno. Tente novamente mais tarde.", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String tipo, String title,
                                                   String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://sgsm.com.br/erros/" + tipo));
        pd.setTitle(title);
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
