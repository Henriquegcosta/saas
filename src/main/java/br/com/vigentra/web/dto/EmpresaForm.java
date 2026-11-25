package br.com.vigentra.web.dto;

import br.com.vigentra.domain.model.Empresa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmpresaForm {

    @NotBlank(message = "Informe o nome da empresa.")
    @Size(max = 150)
    private String nome;

    @Size(max = 18)
    private String cnpj;

    @Email(message = "E-mail inválido.")
    @Size(max = 150)
    private String email;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Telefone inválido.")
    private String telefone;

    @Size(max = 255)
    private String endereco;

    public static EmpresaForm de(Empresa e) {
        EmpresaForm f = new EmpresaForm();
        f.nome = e.getNome();
        f.cnpj = e.getCnpj();
        f.email = e.getEmail();
        f.telefone = e.getTelefone();
        f.endereco = e.getEndereco();
        return f;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
}
