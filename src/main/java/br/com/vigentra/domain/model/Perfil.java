package br.com.vigentra.domain.model;

import br.com.vigentra.domain.enums.PerfilCodigo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Perfil de acesso (Administrador, Gestor, Usuário). Tabela de referência global. */
@Entity
@Table(name = "perfis")
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, unique = true, length = 20)
    private PerfilCodigo codigo;

    @Column(nullable = false, length = 50)
    private String nome;

    @Column(length = 255)
    private String descricao;

    @Column(nullable = false)
    private Integer nivel;

    public Perfil() {}

    public Perfil(PerfilCodigo codigo) {
        this.codigo = codigo;
        this.nome = codigo.getNome();
        this.descricao = codigo.getDescricao();
        this.nivel = codigo.getNivel();
    }

    public Long getId() { return id; }
    public PerfilCodigo getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public Integer getNivel() { return nivel; }
}
