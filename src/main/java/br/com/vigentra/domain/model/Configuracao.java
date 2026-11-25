package br.com.vigentra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;

/** Configurações de alertas, e-mail e preferências do sistema (uma linha por empresa). */
@Entity
@Table(name = "configuracoes")
public class Configuracao extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
    private Empresa empresa;

    /** Dias de antecedência, separados por vírgula. 0 = no dia do vencimento. */
    @Column(name = "dias_alerta", nullable = false, length = 50)
    private String diasAlerta = "30,15,7,0";

    @Column(name = "alertar_apos_vencimento", nullable = false)
    private boolean alertarAposVencimento = true;

    @Column(name = "intervalo_pos_vencimento", nullable = false)
    private Integer intervaloPosVencimento = 7;

    @Column(name = "max_alertas_pos_vencimento", nullable = false)
    private Integer maxAlertasPosVencimento = 3;

    /** Janela (em dias) em que o documento é considerado "próximo do vencimento". */
    @Column(name = "dias_proximo_vencimento", nullable = false)
    private Integer diasProximoVencimento = 30;

    @Column(name = "notificar_responsavel", nullable = false)
    private boolean notificarResponsavel = true;

    @Column(name = "notificar_gestores", nullable = false)
    private boolean notificarGestores = true;

    @Column(name = "email_remetente_nome", length = 100)
    private String emailRemetenteNome;

    @Column(name = "email_resposta", length = 150)
    private String emailResposta;

    @Column(name = "email_copia", length = 500)
    private String emailCopia;

    @Column(name = "email_assinatura", length = 500)
    private String emailAssinatura;

    @Column(name = "itens_por_pagina", nullable = false)
    private Integer itensPorPagina = 10;

    /** Lista ordenada (crescente) e sem repetição dos dias de antecedência. */
    public List<Integer> getDiasAlertaLista() {
        if (diasAlerta == null || diasAlerta.isBlank()) return List.of();
        return Arrays.stream(diasAlerta.split(","))
                .map(String::trim).filter(s -> s.matches("\\d{1,3}"))
                .map(Integer::parseInt).distinct().sorted().toList();
    }

    public List<String> getEmailCopiaLista() {
        if (emailCopia == null || emailCopia.isBlank()) return List.of();
        return Arrays.stream(emailCopia.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
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
    public String getEmailRemetenteNome() { return emailRemetenteNome; }
    public void setEmailRemetenteNome(String v) { this.emailRemetenteNome = v; }
    public String getEmailResposta() { return emailResposta; }
    public void setEmailResposta(String v) { this.emailResposta = v; }
    public String getEmailCopia() { return emailCopia; }
    public void setEmailCopia(String v) { this.emailCopia = v; }
    public String getEmailAssinatura() { return emailAssinatura; }
    public void setEmailAssinatura(String v) { this.emailAssinatura = v; }
    public Integer getItensPorPagina() { return itensPorPagina; }
    public void setItensPorPagina(Integer v) { this.itensPorPagina = v; }
}
