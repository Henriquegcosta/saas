package br.com.vigentra.service.doki;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.StatusCalculator;
import br.com.vigentra.service.doki.RespostaDoki.DocumentoResumo;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Camada de DADOS do Doki: consultas SOMENTE LEITURA.
 *
 * Não acessa repositórios diretamente: reutiliza o {@link DocumentoService}, cujo filtro-base
 * aplica SEMPRE a empresa da sessão autenticada (tenant) e a visibilidade do usuário
 * (quem não tem DOC_VIEW_ALL só vê os próprios documentos). Nenhum identificador de empresa
 * vem do navegador. Não há métodos de gravação ou exclusão aqui.
 */
@Service
@Transactional(readOnly = true)
public class DokiConsultaService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentoService documentoService;

    public DokiConsultaService(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    /** Resultado de uma consulta: parte dos documentos (para exibir) + total real. */
    public record Lista(List<DocumentoResumo> documentos, long total) {}

    public LocalDate hoje() {
        return documentoService.hoje();
    }

    public int diasProximoPadrao() {
        return documentoService.diasProximo();
    }

    public Map<StatusDocumento, Long> contagemPorStatus() {
        return documentoService.contagemPorStatus();
    }

    /** Documentos que vencem de hoje até hoje + dias (inclusive). */
    public Lista proximos(int dias, int limite) {
        LocalDate hoje = documentoService.hoje();
        DocumentoFiltro f = new DocumentoFiltro();
        f.setVencimentoDe(hoje);
        f.setVencimentoAte(hoje.plusDays(dias));
        return pagina(f, limite);
    }

    /** Documentos já vencidos (mais antigos primeiro). */
    public Lista vencidos(int limite) {
        DocumentoFiltro f = new DocumentoFiltro();
        f.setStatus(StatusDocumento.VENCIDO);
        return pagina(f, limite);
    }

    /** Certificados vencidos ou próximos do vencimento (pelo nome do documento ou da categoria). */
    public Lista certificadosAtencao(int limite) {
        List<Documento> docs = Stream.of(StatusDocumento.VENCIDO, StatusDocumento.PROXIMO_VENCIMENTO)
                .flatMap(s -> {
                    DocumentoFiltro f = new DocumentoFiltro();
                    f.setStatus(s);
                    return documentoService.listarTodos(f).stream();
                })
                .filter(DokiConsultaService::ehCertificado)
                .sorted(Comparator.comparing(Documento::getDataVencimento))
                .toList();
        return new Lista(docs.stream().limit(limite).map(this::resumo).toList(), docs.size());
    }

    static boolean ehCertificado(Documento d) {
        return contemCertificado(d.getNome()) || (d.getCategoria() != null && contemCertificado(d.getCategoria().getNome()));
    }

    private static boolean contemCertificado(String texto) {
        if (texto == null) return false;
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).contains("certificad");
    }

    private Lista pagina(DocumentoFiltro f, int limite) {
        Page<Documento> p = documentoService.listar(f, 0, limite);
        return new Lista(p.getContent().stream().map(this::resumo).toList(), p.getTotalElements());
    }

    private DocumentoResumo resumo(Documento d) {
        LocalDate hoje = documentoService.hoje();
        StatusDocumento s = documentoService.status(d);
        return new DocumentoResumo(d.getId(), d.getNome(), d.getCategoria() != null ? d.getCategoria().getNome() : null,
                d.getDataVencimento() != null ? d.getDataVencimento().format(BR) : null,
                StatusCalculator.descreverPrazo(d, hoje), s.getLabel(), s.getCor(), "/app/documentos/" + d.getId());
    }
}
