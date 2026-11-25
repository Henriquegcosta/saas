package br.com.vigentra.domain.enums;

/** Situação de um envio registrado no histórico. */
public enum StatusEnvio {
    PENDENTE("Pendente", "secondary"),
    ENVIADO("Enviado", "success"),
    /** Confirmado pelo provedor (webhook) como entregue ao aparelho/caixa do destinatário. */
    ENTREGUE("Entregue", "success"),
    /** Confirmado pelo provedor (webhook) como lido. */
    LIDO("Lido", "success"),
    FALHA("Falha", "danger"),
    /** Integração em modo desenvolvimento: a mensagem foi gerada e gravada em disco, mas NÃO foi enviada. */
    MODO_DEV("Não enviado (modo desenvolvimento)", "info"),
    /** Destinatário com domínio reservado/de demonstração: bloqueado para proteger a reputação do remetente. */
    IGNORADO("Não enviado (endereço de demonstração)", "secondary");

    private final String label;
    private final String cor;

    StatusEnvio(String label, String cor) {
        this.label = label;
        this.cor = cor;
    }

    public boolean isSucesso() {
        return this == ENVIADO || this == ENTREGUE || this == LIDO || this == MODO_DEV || this == IGNORADO;
    }

    public String getLabel() { return label; }
    public String getCor() { return cor; }
}
