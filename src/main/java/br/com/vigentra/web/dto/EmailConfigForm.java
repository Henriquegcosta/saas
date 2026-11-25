package br.com.vigentra.web.dto;

import br.com.vigentra.domain.model.Configuracao;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Personalização dos e-mails da empresa. As credenciais SMTP NÃO ficam aqui:
 * são definidas por variáveis de ambiente no servidor.
 */
public class EmailConfigForm {

    @NotBlank(message = "Informe o nome do remetente.")
    @Size(max = 100)
    private String emailRemetenteNome;

    @Email(message = "E-mail de resposta inválido.")
    @Size(max = 150)
    private String emailResposta;

    @Size(max = 500)
    @Pattern(regexp = "^$|^[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+(\\s*[,;]\\s*[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+)*$",
            message = "Informe e-mails válidos separados por vírgula.")
    private String emailCopia;

    @Size(max = 500)
    private String emailAssinatura;

    public static EmailConfigForm de(Configuracao c) {
        EmailConfigForm f = new EmailConfigForm();
        f.emailRemetenteNome = c.getEmailRemetenteNome();
        f.emailResposta = c.getEmailResposta();
        f.emailCopia = c.getEmailCopia();
        f.emailAssinatura = c.getEmailAssinatura();
        return f;
    }

    public String getEmailRemetenteNome() { return emailRemetenteNome; }
    public void setEmailRemetenteNome(String v) { this.emailRemetenteNome = v; }
    public String getEmailResposta() { return emailResposta; }
    public void setEmailResposta(String v) { this.emailResposta = v; }
    public String getEmailCopia() { return emailCopia; }
    public void setEmailCopia(String v) { this.emailCopia = v; }
    public String getEmailAssinatura() { return emailAssinatura; }
    public void setEmailAssinatura(String v) { this.emailAssinatura = v; }
}
