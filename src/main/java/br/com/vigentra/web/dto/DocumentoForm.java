package br.com.vigentra.web.dto;

import br.com.vigentra.domain.model.Documento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Formulário de cadastro/edição de documento. */
public class DocumentoForm {

    private Long id;

    @NotBlank(message = "Informe o nome do documento.")
    @Size(max = 150, message = "Máximo de 150 caracteres.")
    private String nome;

    @NotNull(message = "Selecione a categoria.")
    private Long categoriaId;

    @Size(max = 80, message = "Máximo de 80 caracteres.")
    private String numero;

    @Size(max = 2000, message = "Máximo de 2000 caracteres.")
    private String descricao;

    @Size(max = 150, message = "Máximo de 150 caracteres.")
    private String empresaResponsavel;

    @Size(max = 150, message = "Máximo de 150 caracteres.")
    private String responsavelNome;

    @Email(message = "E-mail do responsável inválido.")
    @Size(max = 150)
    private String responsavelEmail;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataEmissao;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataVencimento;

    private boolean semVencimento;

    @Size(max = 2000, message = "Máximo de 2000 caracteres.")
    private String observacoes;

    private boolean removerArquivo;

    public static DocumentoForm de(Documento d) {
        DocumentoForm f = new DocumentoForm();
        f.id = d.getId();
        f.nome = d.getNome();
        f.categoriaId = d.getCategoria().getId();
        f.numero = d.getNumero();
        f.descricao = d.getDescricao();
        f.empresaResponsavel = d.getEmpresaResponsavel();
        if (d.getResponsavel() != null) {
            f.responsavelNome = d.getResponsavel().getNome();
            f.responsavelEmail = d.getResponsavel().getEmail();
        }
        f.dataEmissao = d.getDataEmissao();
        f.dataVencimento = d.getDataVencimento();
        f.semVencimento = d.isSemVencimento();
        f.observacoes = d.getObservacoes();
        return f;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getEmpresaResponsavel() { return empresaResponsavel; }
    public void setEmpresaResponsavel(String v) { this.empresaResponsavel = v; }
    public String getResponsavelNome() { return responsavelNome; }
    public void setResponsavelNome(String responsavelNome) { this.responsavelNome = responsavelNome; }
    public String getResponsavelEmail() { return responsavelEmail; }
    public void setResponsavelEmail(String responsavelEmail) { this.responsavelEmail = responsavelEmail; }
    public LocalDate getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(LocalDate dataEmissao) { this.dataEmissao = dataEmissao; }
    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }
    public boolean isSemVencimento() { return semVencimento; }
    public void setSemVencimento(boolean semVencimento) { this.semVencimento = semVencimento; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public boolean isRemoverArquivo() { return removerArquivo; }
    public void setRemoverArquivo(boolean removerArquivo) { this.removerArquivo = removerArquivo; }
}
