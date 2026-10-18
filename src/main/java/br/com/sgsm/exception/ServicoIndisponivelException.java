package br.com.sgsm.exception;

// Dependência externa (ms-sboot-auth, Redis) fora do ar durante uma operação que não pode seguir sem ela (503)
public class ServicoIndisponivelException extends RuntimeException {
    public ServicoIndisponivelException(String message, Throwable causa) {
        super(message, causa);
    }
}
