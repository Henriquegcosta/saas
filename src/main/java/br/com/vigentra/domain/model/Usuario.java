package br.com.vigentra.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Usuário do sistema. Sempre vinculado a uma única empresa (tenant). */
@Entity
@Table(name = "usuarios")
public class Usuario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    /** Hash BCrypt da senha (nunca a senha em texto puro). */
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Celular para WhatsApp/SMS no formato internacional E.164 (ex.: +5511999990000). */
    @Column(length = 20)
    private String celular;

    /** Preferências de canais e consentimento. A coluna de e-mail mantém o nome legado. */
    @Embedded
    @AttributeOverride(name = "receberEmail", column = @Column(name = "receber_alertas_email", nullable = false))
    private PreferenciasComunicacao preferencias = new PreferenciasComunicacao();

    /** Preenchido quando o usuário confirma o e-mail. Contas não verificadas não acessam a área logada. */
    @Column(name = "email_verificado_em")
    private LocalDateTime emailVerificadoEm;

    /** Novo e-mail aguardando confirmação (o e-mail atual continua valendo até lá). */
    @Column(name = "email_pendente", length = 150)
    private String emailPendente;

    @Column(name = "notificacoes_app", nullable = false)
    private boolean notificacoesApp = true;

    @Column(name = "tentativas_login_falhas", nullable = false)
    private Integer tentativasLoginFalhas = 0;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @Column(name = "termos_aceitos_em")
    private LocalDateTime termosAceitosEm;

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isReceberAlertasEmail() { return getPreferencias().isReceberEmail(); }
    public void setReceberAlertasEmail(boolean v) { getPreferencias().setReceberEmail(v); }
    public PreferenciasComunicacao getPreferencias() {
        if (preferencias == null) preferencias = new PreferenciasComunicacao();
        return preferencias;
    }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public LocalDateTime getEmailVerificadoEm() { return emailVerificadoEm; }
    public void setEmailVerificadoEm(LocalDateTime v) { this.emailVerificadoEm = v; }
    public boolean isEmailVerificado() { return emailVerificadoEm != null; }
    public String getEmailPendente() { return emailPendente; }
    public void setEmailPendente(String emailPendente) { this.emailPendente = emailPendente; }
    public boolean isNotificacoesApp() { return notificacoesApp; }
    public void setNotificacoesApp(boolean v) { this.notificacoesApp = v; }
    public Integer getTentativasLoginFalhas() { return tentativasLoginFalhas; }
    public void setTentativasLoginFalhas(Integer v) { this.tentativasLoginFalhas = v; }
    public LocalDateTime getBloqueadoAte() { return bloqueadoAte; }
    public void setBloqueadoAte(LocalDateTime bloqueadoAte) { this.bloqueadoAte = bloqueadoAte; }
    public LocalDateTime getUltimoLogin() { return ultimoLogin; }
    public void setUltimoLogin(LocalDateTime ultimoLogin) { this.ultimoLogin = ultimoLogin; }
    public LocalDateTime getTermosAceitosEm() { return termosAceitosEm; }
    public void setTermosAceitosEm(LocalDateTime termosAceitosEm) { this.termosAceitosEm = termosAceitosEm; }

    public String getIniciais() {
        if (nome == null || nome.isBlank()) return "?";
        String[] p = nome.trim().split("\\s+");
        String i = p[0].substring(0, 1);
        if (p.length > 1) i += p[p.length - 1].substring(0, 1);
        return i.toUpperCase();
    }
}
