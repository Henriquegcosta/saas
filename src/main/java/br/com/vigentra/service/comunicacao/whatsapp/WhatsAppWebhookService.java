package br.com.vigentra.service.comunicacao.whatsapp;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.service.comunicacao.PreferenciasService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Webhook oficial do WhatsApp Cloud API:
 * - verificação inicial (hub.challenge) com WHATSAPP_WEBHOOK_VERIFY_TOKEN;
 * - assinatura X-Hub-Signature-256 (HMAC-SHA256 com o App Secret) validada em TODA notificação;
 * - atualização do status das mensagens (sent / delivered / read / failed) pelo "wamid";
 * - descadastro: resposta "SAIR", "PARAR", "STOP"... revoga o consentimento de WhatsApp.
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
    private static final Set<String> PALAVRAS_DESCADASTRO = Set.of("SAIR", "PARAR", "STOP", "CANCELAR", "DESCADASTRAR", "NAO", "NÃO");

    private final AppProperties.Whatsapp cfg;
    private final int maxTentativas;
    private final HistoricoEnvioRepository historicoRepo;
    private final PreferenciasService preferencias;
    private final ObjectMapper mapper;

    public WhatsAppWebhookService(AppProperties props, HistoricoEnvioRepository historicoRepo,
                                  PreferenciasService preferencias, ObjectMapper mapper) {
        this.cfg = props.whatsapp();
        this.maxTentativas = props.mail().maxTentativas();
        this.historicoRepo = historicoRepo;
        this.preferencias = preferencias;
        this.mapper = mapper;
    }

    public boolean verificacaoValida(String modo, String token) {
        String esperado = cfg.webhookVerifyToken();
        return "subscribe".equals(modo) && esperado != null && !esperado.isBlank() && token != null
                && MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    /** Valida o cabeçalho X-Hub-Signature-256 ("sha256=<hex>") sobre o corpo BRUTO da requisição. */
    public boolean assinaturaValida(byte[] corpo, String cabecalho) {
        String segredo = cfg.appSecret();
        if (segredo == null || segredo.isBlank() || cabecalho == null || !cabecalho.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperado = "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo));
            return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                    cabecalho.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public void processar(byte[] corpo) {
        JsonNode raiz;
        try {
            raiz = mapper.readTree(corpo);
        } catch (Exception e) {
            log.warn("Webhook WhatsApp com JSON inválido ignorado.");
            return;
        }
        for (JsonNode entry : raiz.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode valor = change.path("value");
                valor.path("statuses").forEach(this::atualizarStatus);
                valor.path("messages").forEach(this::mensagemRecebida);
            }
        }
    }

    private void atualizarStatus(JsonNode st) {
        String id = st.path("id").asText(null);
        if (id == null) return;
        historicoRepo.findFirstByIdExterno(id).ifPresent(h -> {
            switch (st.path("status").asText("")) {
                case "sent" -> { if (h.getStatus() == StatusEnvio.PENDENTE) h.setStatus(StatusEnvio.ENVIADO); }
                case "delivered" -> { if (h.getStatus() != StatusEnvio.LIDO) h.setStatus(StatusEnvio.ENTREGUE); }
                case "read" -> h.setStatus(StatusEnvio.LIDO);
                case "failed" -> {
                    h.setStatus(StatusEnvio.FALHA);
                    JsonNode erro = st.path("errors").path(0);
                    h.setUltimoErro("Meta (código " + erro.path("code").asText("?") + "): "
                            + erro.path("title").asText("") + " " + erro.path("error_data").path("details").asText(""));
                    h.setProximaTentativa(null);   // falhas reportadas pela Meta (ex.: número sem WhatsApp) não são reenviadas
                    h.setTentativas(Math.max(h.getTentativas(), maxTentativas));
                }
                default -> { }
            }
            historicoRepo.save(h);
        });
    }

    private void mensagemRecebida(JsonNode msg) {
        String texto = msg.path("text").path("body").asText(msg.path("button").path("text").asText("")).trim()
                .toUpperCase(Locale.ROOT);
        String de = msg.path("from").asText(null);
        if (de == null || !PALAVRAS_DESCADASTRO.contains(texto)) return;
        int n = preferencias.revogarWhatsappPorCelular("+" + de.replaceAll("\\D", ""));
        log.info("Pedido de descadastro recebido pelo WhatsApp: {} cadastro(s) atualizado(s).", n);
    }
}
