package br.com.vigentra.service.alerta;

/**
 * Resumo de uma execução da rotina de alertas (somando todos os canais).
 * @param mensagensWhatsapp quantas das mensagens processadas foram pelo WhatsApp
 * @param ignorados         destinatários de demonstração/reservados não enviados
 */
public record ResultadoRotina(int documentosAnalisados, int alertasGerados, int enviados, int modoDev,
                              int falhas, int duplicadosIgnorados, int notificacoesApp, int mensagensWhatsapp, int ignorados) {

    public static ResultadoRotina vazio() { return new ResultadoRotina(0, 0, 0, 0, 0, 0, 0, 0, 0); }

    public ResultadoRotina somar(ResultadoRotina o) {
        return new ResultadoRotina(documentosAnalisados + o.documentosAnalisados, alertasGerados + o.alertasGerados,
                enviados + o.enviados, modoDev + o.modoDev, falhas + o.falhas,
                duplicadosIgnorados + o.duplicadosIgnorados, notificacoesApp + o.notificacoesApp,
                mensagensWhatsapp + o.mensagensWhatsapp, ignorados + o.ignorados);
    }

    public String resumo() {
        return documentosAnalisados + " documento(s) analisado(s), " + alertasGerados + " alerta(s) no período, "
                + enviados + " mensagem(ns) enviada(s), " + modoDev + " gerada(s) em modo desenvolvimento (não enviadas), "
                + falhas + " falha(s), " + duplicadosIgnorados + " duplicada(s) ignorada(s), "
                + (ignorados > 0 ? ignorados + " para endereço(s) de demonstração bloqueada(s), " : "")
                + mensagensWhatsapp + " pelo WhatsApp, " + notificacoesApp + " notificação(ões) no sistema";
    }
}
