package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.StatusEnvio;

/** Resultado de uma tentativa de envio em qualquer canal. */
public record ResultadoEnvio(StatusEnvio status, String provedor, String idExterno, String erro) {

    public static ResultadoEnvio enviado(String provedor, String idExterno) {
        return new ResultadoEnvio(StatusEnvio.ENVIADO, provedor, idExterno, null);
    }

    public static ResultadoEnvio dev(String provedor) {
        return new ResultadoEnvio(StatusEnvio.MODO_DEV, provedor, null, null);
    }

    public static ResultadoEnvio falha(String provedor, String erro) {
        String e = erro == null ? "erro desconhecido" : erro;
        return new ResultadoEnvio(StatusEnvio.FALHA, provedor, null, e.length() > 990 ? e.substring(0, 990) : e);
    }

    public static ResultadoEnvio ignorado(String provedor, String motivo) {
        return new ResultadoEnvio(StatusEnvio.IGNORADO, provedor, null, motivo);
    }

    public boolean sucesso() {
        return status.isSucesso();
    }
}
