package br.com.vigentra.web.dto;

import br.com.vigentra.domain.model.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados do próprio usuário (Configurações > Meu perfil). */
public class PerfilUsuarioForm {

    @NotBlank(message = "Informe o nome.")
    @Size(min = 3, max = 150)
    private String nome;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Telefone inválido.")
    private String telefone;

    public static PerfilUsuarioForm de(Usuario u) {
        PerfilUsuarioForm f = new PerfilUsuarioForm();
        f.nome = u.getNome();
        f.telefone = u.getTelefone();
        return f;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
}
