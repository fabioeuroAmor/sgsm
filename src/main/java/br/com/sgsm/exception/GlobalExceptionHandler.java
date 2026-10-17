package br.com.sgsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.sql.SQLException;

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

    // Conflito de horário: 409 com os horários livres mais próximos do mesmo médico
    @ExceptionHandler(HorarioIndisponivelException.class)
    public ResponseEntity<ProblemDetail> handleHorarioIndisponivel(HorarioIndisponivelException ex, HttpServletRequest request) {
        var resposta = problem(HttpStatus.CONFLICT, "horario-indisponivel", "Horário indisponível", ex.getMessage(), request);
        resposta.getBody().setProperty("alternativas", ex.getAlternativas());
        return resposta;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleArgumentoInvalido(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "argumento-invalido", "Requisição inválida", ex.getMessage(), request);
    }

    // Condicao de corrida: duas requisicoes concorrentes passam pela checagem de
    // duplicidade (existsByCpf/existsByEmail) antes de qualquer uma commitar, e a
    // segunda a chegar no banco esbarra na constraint unica -- sem isso, virava 500 cru.
    // A constraint excl_sem_conflito_medico (V013) é a garantia final contra dois agendamentos no
    // mesmo horário — violação dela (SQLState 23P01) é conflito de horário, não dado duplicado
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegridadeDados(DataIntegrityViolationException ex, HttpServletRequest request) {
        if (violacaoDeExclusao(ex)) {
            return handleHorarioIndisponivel(new HorarioIndisponivelException(
                    "Horário indisponível: já existe agendamento para este médico neste período.", null), request);
        }
        return problem(HttpStatus.BAD_REQUEST, "dado-duplicado", "Dado já cadastrado",
                "Um dos dados informados já está em uso. Verifique e tente novamente.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleErroInterno(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro interno",
                "Erro interno. Tente novamente mais tarde.", request);
    }

    public static boolean violacaoDeExclusao(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
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
