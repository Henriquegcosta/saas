package br.com.vigentra.service.comunicacao.whatsapp;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.util.TelefoneUtils;
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
 * WhatsApp Business Cloud API (oficial da Meta):
 * POST {graph}/{versão}/{PHONE_NUMBER_ID}/messages com "type": "template".
 * A resposta traz o "wamid" da mensagem, usado pelo webhook para atualizar entregue/lido/falha.
 */
@Component
public class WhatsAppCloudProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudProvider.class);

    private final AppProperties.Whatsapp cfg;
    private final RestClient http;
    private final ObjectMapper mapper;

    public WhatsAppCloudProvider(AppProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.cfg = props.whatsapp();
        this.http = builder.build();
        this.mapper = mapper;
    }

    @Override public String nome() { return "whatsapp-cloud"; }

    public boolean configurado() { return cfg.configurado(); }

    @Override
    public ResultadoEnvio enviar(MensagemWhatsApp m) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", m.template());
        template.put("language", Map.of("code", m.idioma()));
        if (m.parametros() != null && !m.parametros().isEmpty()) {
            template.put("components", List.of(Map.of("type", "body",
                    "parameters", m.parametros().stream()
                            .map(p -> Map.of("type", "text", "text", MensagemWhatsApp.parametroSeguro(p))).toList())));
        }
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("messaging_product", "whatsapp");
        corpo.put("recipient_type", "individual");
        corpo.put("to", TelefoneUtils.paraWhatsApp(m.para()));
        corpo.put("type", "template");
        corpo.put("template", template);

        String url = cfg.apiUrl() + "/" + cfg.apiVersion() + "/" + cfg.phoneNumberId() + "/messages";
        try {
            return http.post().uri(url)
                    .header("Authorization", "Bearer " + cfg.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .exchange((req, res) -> {
                        String resposta = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        JsonNode json = ler(resposta);
                        if (res.getStatusCode().is2xxSuccessful()) {
                            String id = json.path("messages").path(0).path("id").asText(null);
                            log.info("WhatsApp aceito pela Meta para {} (template {})", m.para(), m.template());
                            return ResultadoEnvio.enviado(nome(), id);
                        }
                        JsonNode e = json.path("error");
                        String erro = "WhatsApp HTTP " + res.getStatusCode().value() + " (código " + e.path("code").asText("?")
                                + "): " + e.path("message").asText("erro desconhecido");
                        String detalhe = e.path("error_data").path("details").asText("");
                        if (!detalhe.isBlank()) erro += " - " + detalhe;
                        log.warn("Falha no WhatsApp para {}: {}", m.para(), erro);
                        return ResultadoEnvio.falha(nome(), erro);
                    });
        } catch (RestClientException e) {
            return ResultadoEnvio.falha(nome(), "API do WhatsApp indisponível: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private JsonNode ler(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
