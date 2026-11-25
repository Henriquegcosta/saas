package br.com.vigentra.util;

/** Mascaramento de dados pessoais para telas de suporte/plataforma (minimização - LGPD). */
public final class Mascaras {

    private Mascaras() {}

    /** fulano.silva@empresa.com -> fu***@empresa.com ; +5511999990001 -> +55 11 9****-0001 */
    public static String destinatario(String valor) {
        if (valor == null || valor.isBlank()) return "";
        if (valor.contains("@")) {
            int i = valor.indexOf('@');
            String local = valor.substring(0, i);
            return (local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***") + valor.substring(i);
        }
        if (valor.startsWith("+55") && valor.length() == 14) {
            return "+55 " + valor.substring(3, 5) + " " + valor.charAt(5) + "****-" + valor.substring(10);
        }
        return valor.length() <= 4 ? "****" : "****" + valor.substring(valor.length() - 4);
    }
}
