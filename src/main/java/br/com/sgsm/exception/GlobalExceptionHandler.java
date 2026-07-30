package br.com.sgsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(IntegracaoException.class)
    public ResponseEntity<ProblemDetail> handleIntegracao(IntegracaoException ex, HttpServletRequest request) {
        return problem(ex.getStatus(), "erro-integracao", "Falha de integração externa", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleErroInterno(Exception ex, HttpServletRequest request) {
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
