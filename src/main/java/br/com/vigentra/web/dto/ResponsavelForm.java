package br.com.vigentra.web.dto;

import br.com.vigentra.domain.model.Responsavel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResponsavelForm {

    private Long id;

    @NotBlank(message = "Informe o nome.")
    @Size(max = 150)
    private String nome;

    @NotBlank(message = "Informe o e-mail (destinatário dos alertas).")
    @Email(message = "E-mail inválido.")
    @Size(max = 150)
    private String email;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Telefone inválido.")
    private String telefone;

    @Size(max = 100)
    private String cargo;

    private Long usuarioId;

    private boolean ativo = true;

    /** Canais, celular e consentimento (aplicados só se o responsável não tiver usuário vinculado). */
    @jakarta.validation.Valid
    private PreferenciasForm preferencias = new PreferenciasForm();

    public static ResponsavelForm de(Responsavel r) {
        ResponsavelForm f = new ResponsavelForm();
        f.id = r.getId();
        f.nome = r.getNome();
        f.email = r.getEmail();
        f.telefone = r.getTelefone();
        f.cargo = r.getCargo();
        f.usuarioId = r.getUsuario() != null ? r.getUsuario().getId() : null;
        f.ativo = r.isAtivo();
        f.preferencias = PreferenciasForm.de(r.getPreferencias(), r.getCelular());
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
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public PreferenciasForm getPreferencias() { return preferencias; }
    public void setPreferencias(PreferenciasForm preferencias) { this.preferencias = preferencias; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
