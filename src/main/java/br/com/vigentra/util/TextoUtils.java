package br.com.vigentra.util;

import java.text.Normalizer;

/** Utilitários de texto: normalização, padrões LIKE, CNPJ e telefone. */
public final class TextoUtils {

    private TextoUtils() {}

    public static String limpar(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Converte o termo de busca no padrão "%termo%" (em minúsculas), ou null se vazio. */
    public static String like(String termo) {
        String t = limpar(termo);
        if (t == null) return null;
        // escapa curingas digitados pelo usuário
        t = t.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + t + "%";
    }

    public static String somenteDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** Valida CNPJ pelos dígitos verificadores. */
    public static boolean cnpjValido(String cnpj) {
        String c = somenteDigitos(cnpj);
        if (c.length() != 14 || c.chars().distinct().count() == 1) return false;
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digito(c, p1) == c.charAt(12) - '0' && digito(c, p2) == c.charAt(13) - '0';
    }

    private static int digito(String c, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) soma += (c.charAt(i) - '0') * pesos[i];
        int r = soma % 11;
        return r < 2 ? 0 : 11 - r;
    }

    public static String formatarCnpj(String cnpj) {
        String c = somenteDigitos(cnpj);
        if (c.length() != 14) return cnpj;
        return c.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    /** Remove acentos e caracteres especiais (usado em nomes de arquivos exportados). */
    public static String slug(String s) {
        if (s == null) return "arquivo";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "").toLowerCase();
    }
}
