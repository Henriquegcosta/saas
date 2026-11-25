package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.CategoriaService;
import br.com.vigentra.service.ResponsavelService;
import br.com.vigentra.service.relatorio.RelatorioDados;
import br.com.vigentra.service.relatorio.RelatorioExportService;
import br.com.vigentra.service.relatorio.RelatorioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Relatórios gerenciais com exportação em PDF, Excel (.xlsx) e CSV. */
@Controller
@RequestMapping("/app/relatorios")
@PreAuthorize("hasAuthority('REPORT_VIEW')")
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final RelatorioExportService exportService;
    private final CategoriaService categoriaService;
    private final ResponsavelService responsavelService;
    private final AuditoriaService auditoria;
    private final ObjectMapper mapper;

    public RelatorioController(RelatorioService relatorioService, RelatorioExportService exportService,
                               CategoriaService categoriaService, ResponsavelService responsavelService,
                               AuditoriaService auditoria, ObjectMapper mapper) {
        this.relatorioService = relatorioService;
        this.exportService = exportService;
        this.categoriaService = categoriaService;
        this.responsavelService = responsavelService;
        this.auditoria = auditoria;
        this.mapper = mapper;
    }

    @GetMapping
    public String relatorio(@ModelAttribute("filtro") DocumentoFiltro filtro, Model model) throws JsonProcessingException {
        RelatorioDados r = relatorioService.gerar(filtro);
        model.addAttribute("r", r);
        model.addAttribute("categorias", categoriaService.todas());
        model.addAttribute("responsaveis", responsavelService.todos());
        model.addAttribute("statusLista", StatusDocumento.values());
        model.addAttribute("graficoMeses", mapper.writeValueAsString(Map.of(
                "labels", r.porMes().stream().map(RelatorioDados.LinhaMes::rotulo).toList(),
                "vencidos", r.porMes().stream().map(RelatorioDados.LinhaMes::vencidos).toList(),
                "aVencer", r.porMes().stream().map(RelatorioDados.LinhaMes::aVencer).toList())));
        model.addAttribute("graficoCategorias", mapper.writeValueAsString(Map.of(
                "labels", r.porCategoria().stream().map(RelatorioDados.LinhaAgrupada::nome).toList(),
                "valores", r.porCategoria().stream().map(RelatorioDados.LinhaAgrupada::total).toList(),
                "cores", r.porCategoria().stream().map(l -> l.cor() == null ? "#2563EB" : l.cor()).toList())));
        return "app/relatorios";
    }

    @GetMapping("/exportar/{formato}")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> exportar(@PathVariable String formato, @ModelAttribute DocumentoFiltro filtro) {
        RelatorioDados r = relatorioService.gerar(filtro);
        String base = "relatorio-documentos-" + r.geradoEm().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        byte[] conteudo;
        MediaType tipo;
        String nome;
        switch (formato) {
            case "pdf" -> { conteudo = exportService.pdf(r); tipo = MediaType.APPLICATION_PDF; nome = base + ".pdf"; }
            case "xlsx" -> { conteudo = exportService.xlsx(r);
                tipo = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); nome = base + ".xlsx"; }
            case "csv" -> { conteudo = exportService.csv(r); tipo = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8); nome = base + ".csv"; }
            default -> { return ResponseEntity.badRequest().build(); }
        }
        auditoria.registrar(AcaoLog.RELATORIO_EXPORTADO, "Relatorio", null,
                "Relatório exportado em " + formato.toUpperCase() + " (" + r.documentos().size() + " documentos; " + r.descricaoFiltros() + ")");
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(conteudo);
    }
}
