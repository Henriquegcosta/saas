package br.com.vigentra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Documento ou certificado controlado. A situação (regular / próximo / vencido)
 * NÃO é armazenada: é sempre calculada a partir de {@link #dataVencimento}.
 */
@Entity
@Table(name = "documentos")
public class Documento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Responsavel responsavel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 80)
    private String numero;

    @Column(length = 2000)
    private String descricao;

    /** Empresa/unidade a que o documento se refere (ex.: filial, fornecedor, funcionário). */
    @Column(name = "empresa_responsavel", length = 150)
    private String empresaResponsavel;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "sem_vencimento", nullable = false)
    private boolean semVencimento = false;

    @Column(length = 2000)
    private String observacoes;

    @Column(name = "arquivo_nome_original", length = 255)
    private String arquivoNomeOriginal;

    /** Nome do arquivo no disco (UUID), nunca exposto diretamente na web. */
    @Column(name = "arquivo_armazenado", length = 255)
    private String arquivoArmazenado;

    @Column(name = "arquivo_tipo", length = 100)
    private String arquivoTipo;

    @Column(name = "arquivo_tamanho")
    private Long arquivoTamanho;

    public boolean temArquivo() { return arquivoArmazenado != null; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public Responsavel getResponsavel() { return responsavel; }
    public void setResponsavel(Responsavel responsavel) { this.responsavel = responsavel; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getEmpresaResponsavel() { return empresaResponsavel; }
    public void setEmpresaResponsavel(String empresaResponsavel) { this.empresaResponsavel = empresaResponsavel; }
    public LocalDate getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(LocalDate dataEmissao) { this.dataEmissao = dataEmissao; }
    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }
    public boolean isSemVencimento() { return semVencimento; }
    public void setSemVencimento(boolean semVencimento) { this.semVencimento = semVencimento; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public String getArquivoNomeOriginal() { return arquivoNomeOriginal; }
    public void setArquivoNomeOriginal(String v) { this.arquivoNomeOriginal = v; }
    public String getArquivoArmazenado() { return arquivoArmazenado; }
    public void setArquivoArmazenado(String v) { this.arquivoArmazenado = v; }
    public String getArquivoTipo() { return arquivoTipo; }
    public void setArquivoTipo(String arquivoTipo) { this.arquivoTipo = arquivoTipo; }
    public Long getArquivoTamanho() { return arquivoTamanho; }
    public void setArquivoTamanho(Long arquivoTamanho) { this.arquivoTamanho = arquivoTamanho; }
}
