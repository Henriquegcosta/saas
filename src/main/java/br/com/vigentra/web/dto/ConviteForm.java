package br.com.vigentra.web.dto;

import br.com.vigentra.domain.enums.PerfilCodigo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Convite de um novo usuário (o convidado define a própria senha ao aceitar). */
public class ConviteForm {

    @Size(max = 150)
    private String nome;

    @NotBlank(message = "Informe o e-mail do convidado.")
    @Email(message = "E-mail inválido.")
    @Size(max = 150)
    private String email;

    @NotNull(message = "Selecione o perfil.")
    private PerfilCodigo perfil = PerfilCodigo.USUARIO;

    private boolean responsavel = true;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public PerfilCodigo getPerfil() { return perfil; }
    public void setPerfil(PerfilCodigo perfil) { this.perfil = perfil; }
    public boolean isResponsavel() { return responsavel; }
    public void setResponsavel(boolean responsavel) { this.responsavel = responsavel; }
}
