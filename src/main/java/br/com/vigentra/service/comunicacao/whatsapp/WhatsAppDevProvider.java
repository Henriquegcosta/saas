package br.com.vigentra.service.comunicacao.whatsapp;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.util.TextoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MODO DESENVOLVIMENTO do WhatsApp (WHATSAPP_DEV_MODE=true): grava em disco o JSON que seria
 * enviado à Meta e a prévia do texto. NADA é enviado e o histórico deixa isso explícito.
 */
@Component
public class WhatsAppDevProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDevProvider.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicLong SEQ = new AtomicLong();

    private final Path dir;
    private final ObjectMapper mapper;

    public WhatsAppDevProvider(AppProperties props, ObjectMapper mapper) {
        this.dir = Path.of(props.whatsapp().devOutputDir());
        this.mapper = mapper;
    }

    @Override public String nome() { return "whatsapp-dev"; }

    public String getDiretorio() { return dir.toAbsolutePath().normalize().toString(); }

    @Override
    public ResultadoEnvio enviar(MensagemWhatsApp m) {
        try {
            Files.createDirectories(dir);
            Map<String, Object> registro = new LinkedHashMap<>();
            registro.put("aviso", "MODO DESENVOLVIMENTO - esta mensagem NAO foi enviada");
            registro.put("para", m.para());
            registro.put("template", m.template());
            registro.put("idioma", m.idioma());
            registro.put("parametros", m.parametros());
            registro.put("previa", m.previa());
            String nome = TS.format(LocalDateTime.now()) + "-" + SEQ.incrementAndGet() + "-" + TextoUtils.slug(m.para()) + ".json";
            Files.writeString(dir.resolve(nome), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registro), StandardCharsets.UTF_8);
            log.warn("[MODO DESENVOLVIMENTO] WhatsApp NÃO enviado. Para: {} | Template: {} | Arquivo: {}", m.para(), m.template(), nome);
            return ResultadoEnvio.dev(nome());
        } catch (Exception e) {
            return ResultadoEnvio.falha(nome(), "Modo desenvolvimento: falha ao gravar mensagem: " + e.getMessage());
        }
    }
}
