package br.com.vigentra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Pessoa responsável por documentos e destinatária dos alertas.
 * Pode (opcionalmente) estar vinculada a um usuário do sistema.
 */
@Entity
@Table(name = "responsaveis",
        uniqueConstraints = @UniqueConstraint(name = "uk_responsavel_empresa_email", columnNames = {"empresa_id", "email"}))
public class Responsavel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(length = 100)
    private String cargo;

    /** Celular para WhatsApp/SMS no formato E.164 (ex.: +5511999990000). */
    @Column(length = 20)
    private String celular;

    /** Usadas apenas quando o responsável NÃO está vinculado a um usuário (o usuário gerencia as suas). */
    @Embedded
    private PreferenciasComunicacao preferencias = new PreferenciasComunicacao();

    @Column(nullable = false)
    private boolean ativo = true;

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public PreferenciasComunicacao getPreferencias() {
        if (preferencias == null) preferencias = new PreferenciasComunicacao();
        return preferencias;
    }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
