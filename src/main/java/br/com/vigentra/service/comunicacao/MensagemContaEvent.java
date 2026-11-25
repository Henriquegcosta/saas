package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.TipoMensagem;

import java.util.Map;

/**
 * Solicitação de e-mail da jornada da conta. Publicada dentro da transação e processada
 * somente após o commit (ver {@link EnvioContaListener}). Os links com tokens existem
 * apenas em memória - nunca são persistidos nem registrados em log.
 */
public record MensagemContaEvent(TipoMensagem tipo, Long empresaId, String para, String nome, String assunto,
                                 String template, Map<String, Object> variaveis) {

    /** Representação segura (sem as variáveis, que podem conter links com tokens). */
    @Override
    public String toString() {
        return "MensagemContaEvent[" + tipo + " para " + para + "]";
    }
}
