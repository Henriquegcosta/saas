package br.com.vigentra.domain.enums;

/** Canais de comunicação suportados. Novos canais entram aqui + uma implementação de provedor. */
public enum CanalEnvio {
    EMAIL("E-mail", "bi-envelope"),
    WHATSAPP("WhatsApp", "bi-whatsapp"),
    SMS("SMS", "bi-chat-dots");

    private final String label;
    private final String icone;

    CanalEnvio(String label, String icone) {
        this.label = label;
        this.icone = icone;
    }

    public String getLabel() { return label; }
    public String getIcone() { return icone; }
}
