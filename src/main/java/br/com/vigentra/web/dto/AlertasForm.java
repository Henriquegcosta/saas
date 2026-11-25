package br.com.vigentra.web.dto;

import br.com.vigentra.domain.model.Configuracao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Configuração da régua de alertas. */
public class AlertasForm {

    @NotBlank(message = "Informe os dias de antecedência.")
    @Pattern(regexp = "^\\s*\\d{1,3}(\\s*,\\s*\\d{1,3})*\\s*$", message = "Use números separados por vírgula. Ex.: 30,15,7,0")
    private String diasAlerta;

    private boolean alertarAposVencimento;

    @NotNull @Min(value = 1, message = "Mínimo de 1 dia.") @Max(value = 90, message = "Máximo de 90 dias.")
    private Integer intervaloPosVencimento;

    @NotNull @Min(value = 1, message = "Mínimo de 1 aviso.") @Max(value = 12, message = "Máximo de 12 avisos.")
    private Integer maxAlertasPosVencimento;

    @NotNull @Min(value = 1, message = "Mínimo de 1 dia.") @Max(value = 365, message = "Máximo de 365 dias.")
    private Integer diasProximoVencimento;

    private boolean notificarResponsavel;
    private boolean notificarGestores;

    public static AlertasForm de(Configuracao c) {
        AlertasForm f = new AlertasForm();
        f.diasAlerta = c.getDiasAlerta();
        f.alertarAposVencimento = c.isAlertarAposVencimento();
        f.intervaloPosVencimento = c.getIntervaloPosVencimento();
        f.maxAlertasPosVencimento = c.getMaxAlertasPosVencimento();
        f.diasProximoVencimento = c.getDiasProximoVencimento();
        f.notificarResponsavel = c.isNotificarResponsavel();
        f.notificarGestores = c.isNotificarGestores();
        return f;
    }

    public String getDiasAlerta() { return diasAlerta; }
    public void setDiasAlerta(String diasAlerta) { this.diasAlerta = diasAlerta; }
    public boolean isAlertarAposVencimento() { return alertarAposVencimento; }
    public void setAlertarAposVencimento(boolean v) { this.alertarAposVencimento = v; }
    public Integer getIntervaloPosVencimento() { return intervaloPosVencimento; }
    public void setIntervaloPosVencimento(Integer v) { this.intervaloPosVencimento = v; }
    public Integer getMaxAlertasPosVencimento() { return maxAlertasPosVencimento; }
    public void setMaxAlertasPosVencimento(Integer v) { this.maxAlertasPosVencimento = v; }
    public Integer getDiasProximoVencimento() { return diasProximoVencimento; }
    public void setDiasProximoVencimento(Integer v) { this.diasProximoVencimento = v; }
    public boolean isNotificarResponsavel() { return notificarResponsavel; }
    public void setNotificarResponsavel(boolean v) { this.notificarResponsavel = v; }
    public boolean isNotificarGestores() { return notificarGestores; }
    public void setNotificarGestores(boolean v) { this.notificarGestores = v; }
}
