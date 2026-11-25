package br.com.vigentra.domain.model;

import br.com.vigentra.domain.enums.PermissaoCodigo;
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

/** Permissão granular. Tabela de referência global. */
@Entity
@Table(name = "permissoes")
public class Permissao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, unique = true, length = 40)
    private PermissaoCodigo codigo;

    @Column(nullable = false, length = 50)
    private String grupo;

    @Column(nullable = false, length = 255)
    private String descricao;

    public Permissao() {}

    public Permissao(PermissaoCodigo codigo) {
        this.codigo = codigo;
        this.grupo = codigo.getGrupo();
        this.descricao = codigo.getDescricao();
    }

    public Long getId() { return id; }
    public PermissaoCodigo getCodigo() { return codigo; }
    public String getGrupo() { return grupo; }
    public String getDescricao() { return descricao; }
}
