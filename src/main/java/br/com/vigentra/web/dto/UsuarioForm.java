package br.com.vigentra.web.dto;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cadastro/edição de usuário pela área administrativa. */
public class UsuarioForm {

    private Long id;

    @NotBlank(message = "Informe o nome.")
    @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
    private String nome;

    @NotBlank(message = "Informe o e-mail.")
    @Email(message = "E-mail inválido.")
    @Size(max = 150)
    private String email;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Telefone inválido.")
    private String telefone;

    @NotNull(message = "Selecione o perfil.")
    private PerfilCodigo perfil;

    /** Obrigatória apenas no cadastro; na edição, em branco mantém a senha atual. */
    @Pattern(regexp = "^$|" + SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM)
    private String senha;

    private boolean ativo = true;

    /** Cria/atualiza automaticamente um "Responsável" vinculado a este usuário. */
    private boolean responsavel = true;

    public static UsuarioForm de(Usuario u) {
        UsuarioForm f = new UsuarioForm();
        f.id = u.getId();
        f.nome = u.getNome();
        f.email = u.getEmail();
        f.telefone = u.getTelefone();
        f.perfil = u.getPerfil().getCodigo();
        f.ativo = u.isAtivo();
        return f;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public PerfilCodigo getPerfil() { return perfil; }
    public void setPerfil(PerfilCodigo perfil) { this.perfil = perfil; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public boolean isResponsavel() { return responsavel; }
    public void setResponsavel(boolean responsavel) { this.responsavel = responsavel; }
}
