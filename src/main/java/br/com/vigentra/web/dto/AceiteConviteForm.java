package br.com.vigentra.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados preenchidos pelo convidado ao aceitar o convite. */
public class AceiteConviteForm {

    private String token;

    @NotBlank(message = "Informe seu nome completo.")
    @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
    private String nome;

    @Pattern(regexp = "^$|^[0-9()+\\-\\s]{10,20}$", message = "Telefone inválido.")
    private String telefone;

    @NotBlank(message = "Crie uma senha.")
    @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM)
    private String senha;

    @NotBlank(message = "Confirme a senha.")
    private String confirmacaoSenha;

    @AssertTrue(message = "É necessário aceitar os Termos de Uso e a Política de Privacidade.")
    private boolean aceiteTermos;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public String getConfirmacaoSenha() { return confirmacaoSenha; }
    public void setConfirmacaoSenha(String v) { this.confirmacaoSenha = v; }
    public boolean isAceiteTermos() { return aceiteTermos; }
    public void setAceiteTermos(boolean aceiteTermos) { this.aceiteTermos = aceiteTermos; }
}
