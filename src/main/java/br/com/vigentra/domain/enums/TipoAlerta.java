package br.com.vigentra.domain.enums;

/** Tipo de alerta de vencimento. */
public enum TipoAlerta {
    ANTECEDENCIA("Vencimento próximo", "warning"),
    VENCE_HOJE("Vence hoje", "danger"),
    VENCIDO("Documento vencido", "danger"),
    SISTEMA("Sistema", "primary");

    private final String label;
    private final String cor;

    TipoAlerta(String label, String cor) {
        this.label = label;
        this.cor = cor;
    }

    public String getLabel() { return label; }
    public String getCor() { return cor; }
}
