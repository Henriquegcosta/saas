package br.com.vigentra.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AlterarSenhaForm {

    /** Usado só na alteração logada; na redefinição por token fica vazio. */
    private String senhaAtual;

    @NotBlank(message = "Informe a nova senha.")
    @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM)
    private String novaSenha;

    @NotBlank(message = "Confirme a nova senha.")
    private String confirmacao;

    private String token;

    public String getSenhaAtual() { return senhaAtual; }
    public void setSenhaAtual(String senhaAtual) { this.senhaAtual = senhaAtual; }
    public String getNovaSenha() { return novaSenha; }
    public void setNovaSenha(String novaSenha) { this.novaSenha = novaSenha; }
    public String getConfirmacao() { return confirmacao; }
    public void setConfirmacao(String confirmacao) { this.confirmacao = confirmacao; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
