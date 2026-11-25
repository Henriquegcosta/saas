package br.com.vigentra.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CategoriaForm {

    private Long id;

    @NotBlank(message = "Informe o nome da categoria.")
    @Size(max = 80)
    private String nome;

    @Size(max = 255)
    private String descricao;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida.")
    private String cor = "#2563EB";

    @Pattern(regexp = "^bi-[a-z0-9-]{1,36}$", message = "Ícone inválido.")
    private String icone = "bi-file-earmark-text";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }
}
