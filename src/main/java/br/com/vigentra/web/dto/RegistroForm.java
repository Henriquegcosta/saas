package br.com.vigentra.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cadastro de nova empresa + usuário administrador. */
public class RegistroForm {

    @NotBlank(message = "Informe seu nome completo.")
    @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
    private String nome;

    @NotBlank(message = "Informe o nome da empresa.")
    @Size(max = 150, message = "Máximo de 150 caracteres.")
    private String empresa;

    @Size(max = 18)
    private String cnpj;

    @NotBlank(message = "Informe o e-mail.")
    @Email(message = "E-mail inválido.")
    @Size(max = 150)
    private String email;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Telefone inválido.")
    private String telefone;

    @NotBlank(message = "Crie uma senha.")
    @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM)
    private String senha;

    @NotBlank(message = "Confirme a senha.")
    private String confirmacaoSenha;

    @AssertTrue(message = "É necessário aceitar os Termos de Uso e a Política de Privacidade.")
    private boolean aceiteTermos;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public String getConfirmacaoSenha() { return confirmacaoSenha; }
    public void setConfirmacaoSenha(String confirmacaoSenha) { this.confirmacaoSenha = confirmacaoSenha; }
    public boolean isAceiteTermos() { return aceiteTermos; }
    public void setAceiteTermos(boolean aceiteTermos) { this.aceiteTermos = aceiteTermos; }
}
