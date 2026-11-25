package br.com.vigentra.service;

/** Violação de regra de negócio, exibida ao usuário como mensagem de erro amigável. */
public class NegocioException extends RuntimeException {
    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
