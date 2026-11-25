package br.com.vigentra.service.comunicacao.whatsapp;

import java.util.List;

/**
 * Mensagem de template do WhatsApp (obrigatório para mensagens iniciadas pela empresa).
 * @param para        celular em E.164 (+5511999990000)
 * @param template    nome do modelo APROVADO na Meta
 * @param idioma      código do idioma do modelo (ex.: pt_BR)
 * @param parametros  valores das variáveis {{1}}, {{2}}... do corpo do modelo
 * @param previa      texto equivalente, usado apenas no modo desenvolvimento e no histórico
 */
public record MensagemWhatsApp(String para, String template, String idioma, List<String> parametros, String previa) {

    /** A Meta não aceita quebras de linha, tabs nem mais de 4 espaços seguidos em parâmetros. */
    public static String parametroSeguro(String valor) {
        if (valor == null || valor.isBlank()) return "-";
        String v = valor.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {4,}", "   ").trim();
        return v.length() > 200 ? v.substring(0, 197) + "..." : v;
    }
}
