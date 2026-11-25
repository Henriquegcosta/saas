package br.com.vigentra.service.comunicacao.email;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.util.TextoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MODO DESENVOLVIMENTO: nenhum e-mail sai do servidor. A mensagem é gravada como .html
 * para inspeção e o histórico registra "Não enviado (modo desenvolvimento)".
 */
@Component
public class DevEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(DevEmailProvider.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicLong SEQ = new AtomicLong();

    private final Path dir;

    public DevEmailProvider(AppProperties props) {
        this.dir = Path.of(props.mail().devOutputDir());
    }

    @Override public String nome() { return "dev"; }
    @Override public boolean configurado() { return true; }

    public String getDiretorio() { return dir.toAbsolutePath().normalize().toString(); }

    @Override
    public ResultadoEnvio enviar(MensagemEmail m) {
        try {
            Files.createDirectories(dir);
            String nome = TS.format(LocalDateTime.now()) + "-" + SEQ.incrementAndGet() + "-" + TextoUtils.slug(m.para()) + ".html";
            String conteudo = "<!-- MODO DESENVOLVIMENTO: este e-mail NAO foi enviado.\n     Para: " + m.para()
                    + "\n     Assunto: " + m.assunto() + " -->\n" + m.html();
            Files.writeString(dir.resolve(nome), conteudo, StandardCharsets.UTF_8);
            // o conteúdo não vai para o log: pode conter links com tokens de uso único
            log.warn("[MODO DESENVOLVIMENTO] E-mail NÃO enviado. Para: {} | Assunto: {} | Arquivo: {}", m.para(), m.assunto(), nome);
            return ResultadoEnvio.dev(nome());
        } catch (IOException e) {
            return ResultadoEnvio.falha(nome(), "Modo desenvolvimento: falha ao gravar e-mail em disco: " + e.getMessage());
        }
    }
}
