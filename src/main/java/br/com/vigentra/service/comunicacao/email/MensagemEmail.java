package br.com.vigentra.service.comunicacao.email;

/**
 * E-mail pronto para envio.
 * @param linkDescadastro URL de descadastro (gera os cabeçalhos List-Unsubscribe / One-Click), opcional
 * @param tag             categoria para estatísticas no provedor (ex.: "alerta", "conta")
 */
public record MensagemEmail(String para, String nomeDestinatario, String nomeRemetente, String responderPara,
                            String assunto, String html, String linkDescadastro, String tag) {}
