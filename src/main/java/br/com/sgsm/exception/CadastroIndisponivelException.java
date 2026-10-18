package br.com.sgsm.exception;

// Auto-cadastro público recusado por dado já existente: mensagem única (não diz se foi CPF ou e-mail)
public class CadastroIndisponivelException extends RuntimeException {
    public CadastroIndisponivelException(String message) {
        super(message);
    }
}
