package br.com.vigentra.domain.model;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.TipoAlerta;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

/**
 * Preferências de comunicação de uma pessoa (usuário ou responsável):
 * canais aceitos, consentimento (LGPD / política da Meta) e tipos de alerta desejados.
 *
 * Regra: WhatsApp e SMS só são usados com consentimento registrado (data + origem).
 */
@Embeddable
public class PreferenciasComunicacao {

    @Column(name = "receber_email", nullable = false)
    private boolean receberEmail = true;

    @Column(name = "receber_whatsapp", nullable = false)
    private boolean receberWhatsapp = false;

    @Column(name = "receber_sms", nullable = false)
    private boolean receberSms = false;

    @Column(name = "whatsapp_consentimento_em")
    private LocalDateTime whatsappConsentimentoEm;

    @Column(name = "whatsapp_consentimento_origem", length = 255)
    private String whatsappConsentimentoOrigem;

    @Column(name = "sms_consentimento_em")
    private LocalDateTime smsConsentimentoEm;

    @Column(name = "sms_consentimento_origem", length = 255)
    private String smsConsentimentoOrigem;

    @Column(name = "alerta_antecedencia", nullable = false)
    private boolean alertaAntecedencia = true;

    @Column(name = "alerta_vence_hoje", nullable = false)
    private boolean alertaVenceHoje = true;

    @Column(name = "alerta_vencido", nullable = false)
    private boolean alertaVencido = true;

    /** A pessoa aceita este canal (preferência ativa + consentimento, quando exigido)? */
    public boolean aceita(CanalEnvio canal) {
        return switch (canal) {
            case EMAIL -> receberEmail;
            case WHATSAPP -> receberWhatsapp && whatsappConsentimentoEm != null;
            case SMS -> receberSms && smsConsentimentoEm != null;
        };
    }

    public boolean aceita(TipoAlerta tipo) {
        return switch (tipo) {
            case ANTECEDENCIA -> alertaAntecedencia;
            case VENCE_HOJE -> alertaVenceHoje;
            case VENCIDO -> alertaVencido;
            case SISTEMA -> true;
        };
    }

    /** Ativa/revoga o WhatsApp registrando (ou limpando) o consentimento. */
    public void definirWhatsapp(boolean ativo, String origem, LocalDateTime quando) {
        receberWhatsapp = ativo;
        if (ativo && whatsappConsentimentoEm == null) {
            whatsappConsentimentoEm = quando;
            whatsappConsentimentoOrigem = origem;
        } else if (!ativo) {
            whatsappConsentimentoEm = null;
            whatsappConsentimentoOrigem = null;
        }
    }

    public void definirSms(boolean ativo, String origem, LocalDateTime quando) {
        receberSms = ativo;
        if (ativo && smsConsentimentoEm == null) {
            smsConsentimentoEm = quando;
            smsConsentimentoOrigem = origem;
        } else if (!ativo) {
            smsConsentimentoEm = null;
            smsConsentimentoOrigem = null;
        }
    }

    /** O consentimento vale para um número específico: trocar o celular exige novo consentimento. */
    public void revogarConsentimentosDeTelefone() {
        definirWhatsapp(false, null, null);
        definirSms(false, null, null);
    }

    public boolean isReceberEmail() { return receberEmail; }
    public void setReceberEmail(boolean v) { this.receberEmail = v; }
    public boolean isReceberWhatsapp() { return receberWhatsapp; }
    public boolean isReceberSms() { return receberSms; }
    public LocalDateTime getWhatsappConsentimentoEm() { return whatsappConsentimentoEm; }
    public String getWhatsappConsentimentoOrigem() { return whatsappConsentimentoOrigem; }
    public LocalDateTime getSmsConsentimentoEm() { return smsConsentimentoEm; }
    public String getSmsConsentimentoOrigem() { return smsConsentimentoOrigem; }
    public boolean isAlertaAntecedencia() { return alertaAntecedencia; }
    public void setAlertaAntecedencia(boolean v) { this.alertaAntecedencia = v; }
    public boolean isAlertaVenceHoje() { return alertaVenceHoje; }
    public void setAlertaVenceHoje(boolean v) { this.alertaVenceHoje = v; }
    public boolean isAlertaVencido() { return alertaVencido; }
    public void setAlertaVencido(boolean v) { this.alertaVencido = v; }
}
