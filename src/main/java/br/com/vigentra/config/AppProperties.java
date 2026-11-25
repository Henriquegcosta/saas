package br.com.vigentra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Propriedades "app.*" do application.yml, com tipagem forte.
 * Registros que contêm segredos sobrescrevem toString() para nunca expô-los em logs.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String baseUrl,
        String timezone,
        String rememberMeKey,
        String secretKey,
        String platformAdmins,
        Storage storage,
        Mail mail,
        Whatsapp whatsapp,
        Sms sms,
        Comunicacao comunicacao,
        Alerts alerts,
        Security security) {

    public record Storage(String dir, List<String> allowedExtensions) {}

    public record Mail(String provider, String from, String fromName, String devOutputDir,
                       int maxTentativas, int intervaloRetentativaMinutos, Brevo brevo) {}

    public record Brevo(String apiKey, String apiUrl) {
        @Override public String toString() { return "Brevo[apiUrl=" + apiUrl + ", apiKey=" + (vazio(apiKey) ? "-" : "***") + "]"; }
    }

    public record Whatsapp(String accessToken, String phoneNumberId, String businessAccountId, String apiUrl,
                           String apiVersion, String appSecret, String webhookVerifyToken, boolean devMode,
                           String devOutputDir, String idioma, String templateProximo, String templateVencido,
                           String templateTeste, String templateTesteIdioma) {
        public boolean configurado() { return !vazio(accessToken) && !vazio(phoneNumberId); }
        @Override public String toString() {
            return "Whatsapp[phoneNumberId=" + phoneNumberId + ", configurado=" + configurado() + ", devMode=" + devMode + "]";
        }
    }

    public record Sms(String provider) {}

    public record Comunicacao(boolean async) {}

    public record Alerts(String cron, String retryCron, boolean enabled) {}

    public record Security(int maxTentativasLogin, int bloqueioMinutos, int maxTentativasIp, int tokenRecuperacaoMinutos) {}

    @Override
    public String toString() {
        return "AppProperties[name=" + name + ", baseUrl=" + baseUrl + ", mail=" + mail + ", whatsapp=" + whatsapp + "]";
    }

    /** E-mails dos operadores da plataforma VIGENTRA (acesso ao painel global de comunicação). */
    public List<String> operadoresPlataforma() {
        if (vazio(platformAdmins)) return List.of();
        return Arrays.stream(platformAdmins.split("[,;\\s]+")).map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty()).toList();
    }

    static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
