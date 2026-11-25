package br.com.vigentra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Convite para uma pessoa entrar na empresa com um perfil definido pelo administrador/gestor.
 * O link carrega um token aleatório; no banco fica apenas o hash. Uso único e com validade.
 */
@Entity
@Table(name = "convites", indexes = @Index(name = "idx_convites_empresa_email", columnList = "empresa_id, email"))
public class Convite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convidado_por_id")
    private Usuario convidadoPor;

    /** Usuário criado quando o convite foi aceito. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 150)
    private String nome;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "aceito_em")
    private LocalDateTime aceitoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "criar_responsavel", nullable = false)
    private boolean criarResponsavel = true;

    public String getSituacao(LocalDateTime agora) {
        if (aceitoEm != null) return "ACEITO";
        if (canceladoEm != null) return "CANCELADO";
        if (!agora.isBefore(expiraEm)) return "EXPIRADO";
        return "PENDENTE";
    }

    public boolean isUtilizavel(LocalDateTime agora) {
        return "PENDENTE".equals(getSituacao(agora));
    }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }
    public Usuario getConvidadoPor() { return convidadoPor; }
    public void setConvidadoPor(Usuario convidadoPor) { this.convidadoPor = convidadoPor; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public LocalDateTime getAceitoEm() { return aceitoEm; }
    public void setAceitoEm(LocalDateTime aceitoEm) { this.aceitoEm = aceitoEm; }
    public LocalDateTime getCanceladoEm() { return canceladoEm; }
    public void setCanceladoEm(LocalDateTime canceladoEm) { this.canceladoEm = canceladoEm; }
    public boolean isCriarResponsavel() { return criarResponsavel; }
    public void setCriarResponsavel(boolean v) { this.criarResponsavel = v; }
}
