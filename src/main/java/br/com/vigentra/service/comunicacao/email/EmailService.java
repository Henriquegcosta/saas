package br.com.vigentra.service.comunicacao.email;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.service.comunicacao.CanalMensageria;
import br.com.vigentra.service.comunicacao.EstadoIntegracao;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.service.comunicacao.StatusIntegracao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canal de e-mail (infraestrutura CENTRALIZADA do VIGENTRA: os clientes não configuram SMTP).
 *
 * Seleção do provedor (MAIL_PROVIDER):
 *  auto  -> Brevo (API) se BREVO_API_KEY existir; senão SMTP se MAIL_HOST existir; senão modo desenvolvimento;
 *  brevo | smtp | dev -> força o provedor.
 */
@Service
public class EmailService implements CanalMensageria {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /** Domínios reservados/fictícios (RFC 2606 / 6761 e os ".demo" dos dados de demonstração). */
    private static final Set<String> TLDS_RESERVADOS = Set.of("demo", "example", "test", "invalid", "localhost", "local");
    private static final Set<String> DOMINIOS_RESERVADOS = Set.of("example.com", "example.org", "example.net");

    private final BrevoEmailProvider brevo;
    private final SmtpEmailProvider smtp;
    private final DevEmailProvider dev;
    private final AppProperties.Mail cfg;
    private final EmailProvider ativo;

    public EmailService(BrevoEmailProvider brevo, SmtpEmailProvider smtp, DevEmailProvider dev, AppProperties props) {
        this.brevo = brevo;
        this.smtp = smtp;
        this.dev = dev;
        this.cfg = props.mail();
        this.ativo = escolher(cfg.provider() == null ? "auto" : cfg.provider().trim().toLowerCase(Locale.ROOT));
        log.info("Canal de e-mail: provedor '{}'{}", ativo.nome(),
                ativo == dev ? " (MODO DESENVOLVIMENTO - nenhum e-mail será enviado)" : "");
    }

    private EmailProvider escolher(String modo) {
        return switch (modo) {
            case "brevo" -> brevo.configurado() ? brevo : avisar("brevo", "BREVO_API_KEY");
            case "smtp" -> smtp.configurado() ? smtp : avisar("smtp", "MAIL_HOST");
            case "dev" -> dev;
            default -> brevo.configurado() ? brevo : smtp.configurado() ? smtp : dev;
        };
    }

    private EmailProvider avisar(String provedor, String variavel) {
        log.error("MAIL_PROVIDER={} mas {} não foi definida: usando modo desenvolvimento.", provedor, variavel);
        return dev;
    }

    @Override public CanalEnvio canal() { return CanalEnvio.EMAIL; }

    public boolean isModoDesenvolvimento() { return ativo == dev; }

    public String getProvedor() { return ativo.nome(); }

    public String getRemetente() { return cfg.from(); }

    public String getDiretorioDev() { return dev.getDiretorio(); }

    @Override
    public StatusIntegracao status() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Remetente", cfg.fromName() + " <" + cfg.from() + ">");
        switch (ativo.nome()) {
            case "brevo" -> d.put("Serviço", "Brevo - API transacional");
            case "smtp" -> d.put("Servidor SMTP", smtp.getHost() + ":" + smtp.getPorta());
            default -> d.put("Arquivos gerados em", dev.getDiretorio());
        }
        d.put("Reenvio automático", cfg.maxTentativas() + " tentativas, a cada " + cfg.intervaloRetentativaMinutos() + " min");
        return switch (ativo.nome()) {
            case "brevo" -> new StatusIntegracao(canal(), "Brevo (API)", EstadoIntegracao.ATIVA,
                    "E-mails transacionais enviados pela API da Brevo.", d);
            case "smtp" -> new StatusIntegracao(canal(), "SMTP", EstadoIntegracao.ATIVA,
                    "E-mails enviados pelo servidor SMTP configurado.", d);
            default -> new StatusIntegracao(canal(), "Desenvolvimento", EstadoIntegracao.MODO_DEV,
                    "Nenhum provedor configurado: as mensagens são geradas e gravadas em disco, mas NÃO são enviadas.", d);
        };
    }

    public ResultadoEnvio enviar(MensagemEmail m) {
        if (enderecoReservado(m.para())) {
            log.info("E-mail para endereço de demonstração/reservado não enviado: {}", m.para());
            return ResultadoEnvio.ignorado(ativo.nome(), "Endereço de demonstração ou domínio reservado - envio bloqueado para "
                    + "proteger a reputação do remetente.");
        }
        return ativo.enviar(m);
    }

    /** Endereços como fulano@empresa.demo ou teste@example.com nunca são enviados a um provedor real. */
    public boolean enderecoReservado(String email) {
        if (ativo == dev || email == null) return false;   // em desenvolvimento tudo é gravado em disco
        String dominio = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        String tld = dominio.substring(dominio.lastIndexOf('.') + 1);
        return TLDS_RESERVADOS.contains(tld) || DOMINIOS_RESERVADOS.contains(dominio);
    }
}
