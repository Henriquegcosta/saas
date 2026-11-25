package br.com.vigentra.web.dto;

import br.com.vigentra.domain.model.PreferenciasComunicacao;
import br.com.vigentra.util.TelefoneUtils;
import jakarta.validation.constraints.Size;

/**
 * Preferências de comunicação (usado pelo próprio usuário, pelo gestor para um responsável
 * e pelo responsável no link público). O consentimento precisa ser marcado explicitamente.
 */
public class PreferenciasForm {

    @Size(max = 25)
    private String celular;

    private boolean receberEmail = true;
    private boolean receberWhatsapp;
    private boolean consentimentoWhatsapp;
    private boolean receberSms;
    private boolean consentimentoSms;
    private boolean alertaAntecedencia = true;
    private boolean alertaVenceHoje = true;
    private boolean alertaVencido = true;
    /** Apenas para usuários: notificações no sino do sistema. */
    private boolean notificacoesApp = true;

    public static PreferenciasForm de(PreferenciasComunicacao p, String celular) {
        PreferenciasForm f = new PreferenciasForm();
        f.celular = celular == null ? null : TelefoneUtils.formatar(celular);
        f.receberEmail = p.isReceberEmail();
        f.receberWhatsapp = p.isReceberWhatsapp();
        f.consentimentoWhatsapp = p.getWhatsappConsentimentoEm() != null;
        f.receberSms = p.isReceberSms();
        f.consentimentoSms = p.getSmsConsentimentoEm() != null;
        f.alertaAntecedencia = p.isAlertaAntecedencia();
        f.alertaVenceHoje = p.isAlertaVenceHoje();
        f.alertaVencido = p.isAlertaVencido();
        return f;
    }

    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public boolean isReceberEmail() { return receberEmail; }
    public void setReceberEmail(boolean v) { this.receberEmail = v; }
    public boolean isReceberWhatsapp() { return receberWhatsapp; }
    public void setReceberWhatsapp(boolean v) { this.receberWhatsapp = v; }
    public boolean isConsentimentoWhatsapp() { return consentimentoWhatsapp; }
    public void setConsentimentoWhatsapp(boolean v) { this.consentimentoWhatsapp = v; }
    public boolean isReceberSms() { return receberSms; }
    public void setReceberSms(boolean v) { this.receberSms = v; }
    public boolean isConsentimentoSms() { return consentimentoSms; }
    public void setConsentimentoSms(boolean v) { this.consentimentoSms = v; }
    public boolean isAlertaAntecedencia() { return alertaAntecedencia; }
    public void setAlertaAntecedencia(boolean v) { this.alertaAntecedencia = v; }
    public boolean isAlertaVenceHoje() { return alertaVenceHoje; }
    public void setAlertaVenceHoje(boolean v) { this.alertaVenceHoje = v; }
    public boolean isAlertaVencido() { return alertaVencido; }
    public void setAlertaVencido(boolean v) { this.alertaVencido = v; }
    public boolean isNotificacoesApp() { return notificacoesApp; }
    public void setNotificacoesApp(boolean v) { this.notificacoesApp = v; }
}
