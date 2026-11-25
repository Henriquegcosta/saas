package br.com.vigentra.service.comunicacao.email;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/** Envio por SMTP (alternativa à API). Credenciais em MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD. */
@Component
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties mailProperties;
    private final AppProperties.Mail cfg;

    public SmtpEmailProvider(ObjectProvider<JavaMailSender> mailSender, MailProperties mailProperties, AppProperties props) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.cfg = props.mail();
    }

    @Override public String nome() { return "smtp"; }

    @Override
    public boolean configurado() {
        return StringUtils.hasText(mailProperties.getHost());
    }

    public String getHost() { return mailProperties.getHost(); }
    public Integer getPorta() { return mailProperties.getPort(); }

    @Override
    public ResultadoEnvio enviar(MensagemEmail m) {
        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) return ResultadoEnvio.falha(nome(), "JavaMailSender indisponível");
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            h.setFrom(new InternetAddress(cfg.from(), StringUtils.hasText(m.nomeRemetente()) ? m.nomeRemetente() : cfg.fromName(),
                    StandardCharsets.UTF_8.name()));
            h.setTo(m.para());
            if (StringUtils.hasText(m.responderPara())) h.setReplyTo(m.responderPara());
            h.setSubject(m.assunto());
            h.setText(m.html(), true);
            if (m.linkDescadastro() != null) {
                mime.addHeader("List-Unsubscribe", "<" + m.linkDescadastro() + ">");
                mime.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            sender.send(mime);
            log.info("E-mail enviado via SMTP para {} - {}", m.para(), m.assunto());
            return ResultadoEnvio.enviado(nome(), mime.getMessageID());
        } catch (Exception e) {
            String erro = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "erro desconhecido" : e.getMessage());
            log.warn("Falha SMTP para {}: {}", m.para(), erro);
            return ResultadoEnvio.falha(nome(), erro);
        }
    }
}
