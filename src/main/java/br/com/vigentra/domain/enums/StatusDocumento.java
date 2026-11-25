package br.com.vigentra.domain.enums;

/** Situação de um documento, sempre calculada a partir da data de vencimento. */
public enum StatusDocumento {
    REGULAR("Regular", "success", "bi-check-circle"),
    PROXIMO_VENCIMENTO("Próximo do vencimento", "warning", "bi-exclamation-triangle"),
    VENCIDO("Vencido", "danger", "bi-x-octagon"),
    SEM_VENCIMENTO("Sem vencimento", "secondary", "bi-infinity");

    private final String label;
    private final String cor;
    private final String icone;

    StatusDocumento(String label, String cor, String icone) {
        this.label = label;
        this.cor = cor;
        this.icone = icone;
    }

    public String getLabel() { return label; }
    public String getCor() { return cor; }
    public String getIcone() { return icone; }
}
