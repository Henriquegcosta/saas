package br.com.vigentra.util;

/**
 * Telefones no formato internacional E.164 (+[país][número]), exigido pela API do WhatsApp.
 * Números brasileiros digitados sem o código do país recebem +55.
 */
public final class TelefoneUtils {

    private TelefoneUtils() {}

    /** @return o número em E.164 (ex.: +5511999990000) ou null se vazio. */
    public static String normalizarCelular(String entrada) {
        if (entrada == null || entrada.isBlank()) return null;
        boolean internacional = entrada.trim().startsWith("+");
        String d = TextoUtils.somenteDigitos(entrada);
        if (!internacional) {
            if (d.startsWith("0")) d = d.replaceFirst("^0+", "");        // 0 de longa distância
            if (d.length() == 10 || d.length() == 11) d = "55" + d;      // DDD + número
        }
        if (d.length() < 10 || d.length() > 15) throw new IllegalArgumentException("Número de celular inválido.");
        if (d.startsWith("55")) {
            String nacional = d.substring(2);
            // Brasil: DDD (2 dígitos) + celular de 9 dígitos iniciando em 9
            if (nacional.length() != 11 || nacional.charAt(2) != '9') {
                throw new IllegalArgumentException("Informe um celular brasileiro com DDD, ex.: (11) 99999-0000.");
            }
        }
        return "+" + d;
    }

    /** Versão que não lança exceção (retorna null para números inválidos). */
    public static String normalizarOuNulo(String entrada) {
        try {
            return normalizarCelular(entrada);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** +5511999990000 -> +55 (11) 99999-0000 */
    public static String formatar(String e164) {
        if (e164 == null) return "";
        if (e164.startsWith("+55") && e164.length() == 14) {
            return "+55 (" + e164.substring(3, 5) + ") " + e164.substring(5, 10) + "-" + e164.substring(10);
        }
        return e164;
    }

    /** Formato aceito pela API da Meta no campo "to": apenas dígitos. */
    public static String paraWhatsApp(String e164) {
        return TextoUtils.somenteDigitos(e164);
    }
}
