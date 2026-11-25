package br.com.vigentra.service.comunicacao.email;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envio pela API transacional da Brevo (POST /v3/smtp/email).
 * Escolhida por ter plano gratuito (300 e-mails/dia) e permitir validar um remetente
 * sem domínio próprio - adequado ao projeto. Outro provedor (SES, Resend, SendGrid...)
 * pode ser adicionado implementando {@link EmailProvider}.
 */
@Component
public class BrevoEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailProvider.class);

    private final AppProperties.Mail cfg;
    private final RestClient http;
    private final ObjectMapper mapper;

    public BrevoEmailProvider(AppProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.cfg = props.mail();
        this.http = builder.build();
        this.mapper = mapper;
    }

    @Override public String nome() { return "brevo"; }

    @Override
    public boolean configurado() {
        return cfg.brevo() != null && cfg.brevo().apiKey() != null && !cfg.brevo().apiKey().isBlank();
    }

    @Override
    public ResultadoEnvio enviar(MensagemEmail m) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("sender", Map.of("name", m.nomeRemetente() == null ? cfg.fromName() : m.nomeRemetente(), "email", cfg.from()));
        Map<String, String> to = new LinkedHashMap<>();
        to.put("email", m.para());
        if (m.nomeDestinatario() != null && !m.nomeDestinatario().isBlank()) to.put("name", m.nomeDestinatario());
        corpo.put("to", List.of(to));
        corpo.put("subject", m.assunto());
        corpo.put("htmlContent", m.html());
        if (m.responderPara() != null && !m.responderPara().isBlank()) corpo.put("replyTo", Map.of("email", m.responderPara()));
        if (m.linkDescadastro() != null) {
            corpo.put("headers", Map.of("List-Unsubscribe", "<" + m.linkDescadastro() + ">",
                    "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
        }
        if (m.tag() != null) corpo.put("tags", List.of(m.tag()));
        try {
            return http.post().uri(cfg.brevo().apiUrl() + "/smtp/email")
                    .header("api-key", cfg.brevo().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .exchange((req, res) -> {
                        String resposta = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (res.getStatusCode().is2xxSuccessful()) {
                            log.info("E-mail aceito pela Brevo para {} ({})", m.para(), m.assunto());
                            return ResultadoEnvio.enviado(nome(), ler(resposta, "messageId"));
                        }
                        String erro = "Brevo HTTP " + res.getStatusCode().value() + ": " + ler(resposta, "code")
                                + " - " + ler(resposta, "message");
                        log.warn("Falha na Brevo para {}: {}", m.para(), erro);
                        return ResultadoEnvio.falha(nome(), erro);
                    });
        } catch (RestClientException e) {
            return ResultadoEnvio.falha(nome(), "Brevo indisponível: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String ler(String json, String campo) {
        try {
            JsonNode n = mapper.readTree(json).get(campo);
            return n == null ? "" : n.asText();
        } catch (Exception e) {
            return "";
        }
    }
}
