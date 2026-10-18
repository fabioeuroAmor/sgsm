package br.com.sgsm.exception;

// Limite de requisições de um endpoint público excedido (429)
public class MuitasTentativasException extends RuntimeException {
    public MuitasTentativasException(String message) {
        super(message);
    }
}
