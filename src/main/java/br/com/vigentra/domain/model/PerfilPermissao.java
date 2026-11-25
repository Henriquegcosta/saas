package br.com.vigentra.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Matriz Perfil x Permissão de cada empresa (permite personalizar acessos por tenant). */
@Entity
@Table(name = "perfil_permissoes",
        uniqueConstraints = @UniqueConstraint(name = "uk_perfil_permissao",
                columnNames = {"empresa_id", "perfil_id", "permissao_id"}))
public class PerfilPermissao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @ManyToOne(optional = false)
    @JoinColumn(name = "permissao_id", nullable = false)
    private Permissao permissao;

    public PerfilPermissao() {}

    public PerfilPermissao(Empresa empresa, Perfil perfil, Permissao permissao) {
        this.empresa = empresa;
        this.perfil = perfil;
        this.permissao = permissao;
    }

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public Perfil getPerfil() { return perfil; }
    public Permissao getPermissao() { return permissao; }
}
