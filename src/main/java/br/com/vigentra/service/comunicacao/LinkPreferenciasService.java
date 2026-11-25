package br.com.vigentra.service.comunicacao;

import br.com.vigentra.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Links assinados (HMAC-SHA256) para o destinatário gerenciar preferências ou se descadastrar
 * sem precisar de login. A assinatura cobre o tipo, o id e o e-mail: alterar qualquer parâmetro
 * invalida o link, e trocar o e-mail do destinatário revoga os links antigos.
 * Nada é armazenado no banco.
 */
@Service
public class LinkPreferenciasService {

    private static final Logger log = LoggerFactory.getLogger(LinkPreferenciasService.class);

    /** R = responsável (sem login) ; U = usuário do sistema */
    public enum Alvo { R, U }

    private final byte[] chave;
    private final String baseUrl;

    public LinkPreferenciasService(AppProperties props) {
        String segredo = props.secretKey();
        if (segredo == null || segredo.isBlank()) {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b);
            this.chave = b;
            log.warn("APP_SECRET_KEY não definida: links de preferências/descadastro expiram ao reiniciar a aplicação.");
        } else {
            this.chave = segredo.getBytes(StandardCharsets.UTF_8);
        }
        this.baseUrl = props.baseUrl();
    }

    public String assinar(Alvo alvo, Long id, String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            byte[] h = mac.doFinal((alvo + "|" + id + "|" + email.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar link", e);
        }
    }

    /** Comparação em tempo constante (evita ataques de temporização). */
    public boolean valido(Alvo alvo, Long id, String email, String assinatura) {
        if (alvo == null || id == null || email == null || assinatura == null) return false;
        return MessageDigest.isEqual(assinar(alvo, id, email).getBytes(StandardCharsets.UTF_8),
                assinatura.getBytes(StandardCharsets.UTF_8));
    }

    public String linkPreferencias(Alvo alvo, Long id, String email) {
        return baseUrl + "/preferencias?a=" + alvo + "&id=" + id + "&t=" + assinar(alvo, id, email);
    }

    /** URL usada no cabeçalho List-Unsubscribe (descadastro com um clique - RFC 8058). */
    public String linkDescadastro(Alvo alvo, Long id, String email) {
        return baseUrl + "/preferencias/descadastrar?a=" + alvo + "&id=" + id + "&t=" + assinar(alvo, id, email);
    }
}
