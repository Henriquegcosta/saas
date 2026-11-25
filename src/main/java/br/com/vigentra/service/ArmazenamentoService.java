package br.com.vigentra.service;

import br.com.vigentra.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Armazenamento seguro dos anexos:
 * - diretório FORA da área pública (não acessível por URL direta);
 * - uma pasta por empresa (isolamento físico por tenant);
 * - nome no disco aleatório (UUID) - o nome original fica só no banco;
 * - validação de extensão, tipo MIME e ASSINATURA BINÁRIA ("magic bytes"),
 *   impedindo, por exemplo, um executável renomeado para .pdf.
 */
@Service
public class ArmazenamentoService {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoService.class);
    private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;

    private static final Map<String, String> MIME = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp");

    private final Path raiz;
    private final List<String> permitidas;

    public ArmazenamentoService(AppProperties props) {
        this.raiz = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        this.permitidas = props.storage().allowedExtensions().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(raiz);
        log.info("Diretório de anexos: {}", raiz);
    }

    public record ArquivoSalvo(String nomeArmazenado, String nomeOriginal, String tipo, long tamanho) {}

    public ArquivoSalvo salvar(Long empresaId, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new NegocioException("Arquivo vazio.");
        if (arquivo.getSize() > TAMANHO_MAXIMO) throw new NegocioException("O arquivo deve ter no máximo 10 MB.");

        String original = nomeSeguro(arquivo.getOriginalFilename());
        String ext = extensao(original);
        if (!permitidas.contains(ext) || !MIME.containsKey(ext)) {
            throw new NegocioException("Formato não permitido. Envie PDF, PNG, JPG ou WEBP.");
        }
        try (InputStream in = arquivo.getInputStream()) {
            byte[] cabecalho = in.readNBytes(12);
            if (!assinaturaConfere(ext, cabecalho)) {
                throw new NegocioException("O conteúdo do arquivo não corresponde a um " + ext.toUpperCase() + " válido.");
            }
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler o arquivo enviado.");
        }

        String nomeDisco = UUID.randomUUID() + "." + ext;
        Path destino = resolver(empresaId, nomeDisco);
        try {
            Files.createDirectories(destino.getParent());
            try (InputStream in = arquivo.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Falha ao gravar anexo em {}", destino, e);
            throw new NegocioException("Falha ao armazenar o arquivo. Tente novamente.");
        }
        return new ArquivoSalvo(nomeDisco, original, MIME.get(ext), arquivo.getSize());
    }

    public Resource carregar(Long empresaId, String nomeArmazenado) {
        Path p = resolver(empresaId, nomeArmazenado);
        if (!Files.isReadable(p)) throw new RecursoNaoEncontradoException("Arquivo não encontrado.");
        return new FileSystemResource(p);
    }

    public void excluir(Long empresaId, String nomeArmazenado) {
        if (nomeArmazenado == null) return;
        try {
            Files.deleteIfExists(resolver(empresaId, nomeArmazenado));
        } catch (IOException e) {
            log.warn("Não foi possível excluir o anexo {}: {}", nomeArmazenado, e.getMessage());
        }
    }

    /** Resolve o caminho e impede "path traversal" (ex.: ../../etc/passwd). */
    private Path resolver(Long empresaId, String nome) {
        if (nome == null || !nome.matches("[A-Za-z0-9-]+\\.[a-z0-9]{2,5}")) {
            throw new RecursoNaoEncontradoException("Arquivo inválido.");
        }
        Path p = raiz.resolve("empresa-" + empresaId).resolve(nome).normalize();
        if (!p.startsWith(raiz)) throw new SecurityException("Caminho inválido");
        return p;
    }

    private static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) return "arquivo";
        String base = Path.of(nome.replace("\\", "/")).getFileName().toString();
        base = base.replaceAll("[\\r\\n\"<>|:*?]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String extensao(String nome) {
        int i = nome.lastIndexOf('.');
        return i < 0 ? "" : nome.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static boolean assinaturaConfere(String ext, byte[] b) {
        return switch (ext) {
            case "pdf" -> comeca(b, 0x25, 0x50, 0x44, 0x46);                     // %PDF
            case "png" -> comeca(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> comeca(b, 0xFF, 0xD8, 0xFF);
            case "webp" -> comeca(b, 0x52, 0x49, 0x46, 0x46) && b.length >= 12   // RIFF....WEBP
                    && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50;
            default -> false;
        };
    }

    private static boolean comeca(byte[] b, int... assinatura) {
        if (b.length < assinatura.length) return false;
        for (int i = 0; i < assinatura.length; i++) if ((b[i] & 0xFF) != assinatura[i]) return false;
        return true;
    }

    public Path getRaiz() { return raiz; }

    public List<String> getPermitidas() { return Arrays.asList(permitidas.toArray(new String[0])); }
}
