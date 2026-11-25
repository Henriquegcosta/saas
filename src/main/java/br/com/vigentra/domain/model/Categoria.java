package br.com.vigentra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Categoria de documento (Alvará, Certidão, CNH...). Cada empresa tem as suas. */
@Entity
@Table(name = "categorias",
        uniqueConstraints = @UniqueConstraint(name = "uk_categoria_empresa_nome", columnNames = {"empresa_id", "nome"}))
public class Categoria extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(length = 255)
    private String descricao;

    /** Cor em hexadecimal (#RRGGBB) usada nos gráficos e etiquetas. */
    @Column(nullable = false, length = 7)
    private String cor = "#2563EB";

    @Column(nullable = false, length = 40)
    private String icone = "bi-file-earmark-text";

    @Column(nullable = false)
    private boolean ativo = true;

    public Categoria() {}

    public Categoria(Empresa empresa, String nome, String descricao, String cor, String icone) {
        this.empresa = empresa;
        this.nome = nome;
        this.descricao = descricao;
        this.cor = cor;
        this.icone = icone;
    }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
