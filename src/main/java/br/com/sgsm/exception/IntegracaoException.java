package br.com.sgsm.exception;

import org.springframework.http.HttpStatus;

// Falha ao integrar com um servico externo/outro microsservico (Evolution API, sgsm-ia).
public class IntegracaoException extends RuntimeException {

    private final HttpStatus status;

    public IntegracaoException(String message, HttpStatus status, Throwable causa) {
        super(message, causa);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
