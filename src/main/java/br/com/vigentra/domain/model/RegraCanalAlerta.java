package br.com.vigentra.domain.model;

import br.com.vigentra.domain.enums.CanalEnvio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Quais canais a empresa usa em cada prazo da régua de alertas.
 * "periodo" = D30, D15, D7, D0 (no dia) ou POS (após o vencimento).
 */
@Entity
@Table(name = "regras_canais_alerta",
        uniqueConstraints = @UniqueConstraint(name = "uk_regra_empresa_periodo", columnNames = {"empresa_id", "periodo"}))
public class RegraCanalAlerta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 10)
    private String periodo;

    @Column(name = "canal_email", nullable = false)
    private boolean email = true;

    @Column(name = "canal_whatsapp", nullable = false)
    private boolean whatsapp = false;

    @Column(name = "canal_sms", nullable = false)
    private boolean sms = false;

    public RegraCanalAlerta() {}

    public RegraCanalAlerta(Empresa empresa, String periodo, boolean email, boolean whatsapp, boolean sms) {
        this.empresa = empresa;
        this.periodo = periodo;
        this.email = email;
        this.whatsapp = whatsapp;
        this.sms = sms;
    }

    public boolean usa(CanalEnvio canal) {
        return switch (canal) {
            case EMAIL -> email;
            case WHATSAPP -> whatsapp;
            case SMS -> sms;
        };
    }

    /** Rótulo amigável do prazo. */
    public String getPeriodoLabel() {
        if ("D0".equals(periodo)) return "No dia do vencimento";
        if ("POS".equals(periodo)) return "Após o vencimento";
        return periodo.substring(1) + " dias antes";
    }

    /** Usado para ordenar: 30, 15, 7, 0, pós. */
    public int getOrdem() {
        return "POS".equals(periodo) ? -1 : Integer.parseInt(periodo.substring(1));
    }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public boolean isEmail() { return email; }
    public void setEmail(boolean email) { this.email = email; }
    public boolean isWhatsapp() { return whatsapp; }
    public void setWhatsapp(boolean whatsapp) { this.whatsapp = whatsapp; }
    public boolean isSms() { return sms; }
    public void setSms(boolean sms) { this.sms = sms; }
}
