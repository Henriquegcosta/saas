package br.com.vigentra.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Registro inexistente OU pertencente a outra empresa. Propositalmente as duas
 * situações retornam 404, para não revelar a existência de dados de outros tenants.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
