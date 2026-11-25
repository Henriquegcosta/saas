package br.com.vigentra.domain.enums;

import java.time.Duration;

/** Tipos de token de uso único enviados por e-mail. */
public enum TipoToken {
    CONFIRMACAO_EMAIL(Duration.ofHours(48)),
    ALTERACAO_EMAIL(Duration.ofHours(24)),
    RECUPERACAO_SENHA(Duration.ofMinutes(30));

    private final Duration validade;

    TipoToken(Duration validade) {
        this.validade = validade;
    }

    public Duration getValidade() { return validade; }
}
