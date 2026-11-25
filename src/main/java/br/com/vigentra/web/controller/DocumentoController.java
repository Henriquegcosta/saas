package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.ArmazenamentoService;
import br.com.vigentra.service.CategoriaService;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.NotificacaoService;
import br.com.vigentra.service.RecursoNaoEncontradoException;
import br.com.vigentra.service.ResponsavelService;
import br.com.vigentra.web.dto.DocumentoForm;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

/** Gestão de documentos: listagem com filtros, cadastro, edição, detalhes, exclusão e anexos. */
@Controller
@RequestMapping("/app/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final CategoriaService categoriaService;
    private final ResponsavelService responsavelService;
    private final NotificacaoService notificacaoService;
    private final ConfiguracaoService configuracaoService;
    private final ArmazenamentoService armazenamento;

    public DocumentoController(DocumentoService documentoService, CategoriaService categoriaService,
                               ResponsavelService responsavelService, NotificacaoService notificacaoService,
                               ConfiguracaoService configuracaoService, ArmazenamentoService armazenamento) {
        this.documentoService = documentoService;
        this.categoriaService = categoriaService;
        this.responsavelService = responsavelService;
        this.notificacaoService = notificacaoService;
        this.configuracaoService = configuracaoService;
        this.armazenamento = armazenamento;
    }

    @GetMapping
    public String listar(@ModelAttribute("filtro") DocumentoFiltro filtro,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        int tamanho = configuracaoService.atual().getItensPorPagina();
        model.addAttribute("pagina", documentoService.listar(filtro, pagina, tamanho));
        var contagens = new java.util.HashMap<String, Long>();
        documentoService.contagemPorStatus().forEach((s, n) -> contagens.put(s.name(), n));
        contagens.put("TOTAL", contagens.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("contagens", contagens);
        model.addAttribute("categorias", categoriaService.todas());
        model.addAttribute("responsaveis", responsavelService.todos());
        model.addAttribute("statusLista", StatusDocumento.values());
        return "app/documentos/lista";
    }

    @GetMapping("/novo")
    @PreAuthorize("hasAuthority('DOC_CREATE')")
    public String novo(Model model) {
        DocumentoForm f = new DocumentoForm();
        SecurityUtils.usuarioOpcional().ifPresent(u -> {
            f.setResponsavelNome(u.getNome());
            f.setResponsavelEmail(u.getEmail());
        });
        return formulario(f, null, model);
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        Documento d = documentoService.buscar(id);
        model.addAttribute("doc", d);
        model.addAttribute("envios", notificacaoService.historicoDoDocumento(id));
        return "app/documentos/detalhe";
    }

    @GetMapping("/{id}/editar")
    @PreAuthorize("hasAuthority('DOC_EDIT')")
    public String editar(@PathVariable Long id, Model model) {
        Documento d = documentoService.buscar(id);
        return formulario(DocumentoForm.de(d), d, model);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('DOC_CREATE','DOC_EDIT')")
    public String salvar(@Valid @ModelAttribute("form") DocumentoForm form, BindingResult br,
                         @RequestParam(value = "arquivo", required = false) MultipartFile arquivo,
                         Model model, RedirectAttributes ra) {
        Documento existente = form.getId() != null ? documentoService.buscar(form.getId()) : null;
        if (br.hasErrors()) return formulario(form, existente, model);
        try {
            Documento d = documentoService.salvar(form, arquivo);
            ra.addFlashAttribute("sucesso", form.getId() == null ? "Documento cadastrado com sucesso." : "Alterações salvas com sucesso.");
            return "redirect:/app/documentos/" + d.getId();
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return formulario(form, existente, model);
        }
    }

    @PostMapping("/{id}/excluir")
    @PreAuthorize("hasAuthority('DOC_DELETE')")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        documentoService.excluir(id);
        ra.addFlashAttribute("sucesso", "Documento excluído.");
        return "redirect:/app/documentos";
    }

    /** Download/visualização do anexo - sempre passando pela checagem de tenant e visibilidade. */
    @GetMapping("/{id}/arquivo")
    public ResponseEntity<Resource> arquivo(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        Documento d = documentoService.buscar(id);
        if (!d.temArquivo()) throw new RecursoNaoEncontradoException("Este documento não possui anexo.");
        Resource r = armazenamento.carregar(d.getEmpresa().getId(), d.getArquivoArmazenado());
        ContentDisposition cd = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(d.getArquivoNomeOriginal(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getArquivoTipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(r);
    }

    private String formulario(DocumentoForm f, Documento existente, Model model) {
        model.addAttribute("form", f);
        model.addAttribute("doc", existente);
        var categorias = new java.util.ArrayList<>(categoriaService.ativas());
        // mantém selecionável a categoria atual do documento, mesmo que tenha sido desativada
        if (existente != null && categorias.stream().noneMatch(c -> c.getId().equals(existente.getCategoria().getId()))) {
            categorias.add(existente.getCategoria());
        }
        model.addAttribute("categorias", categorias);
        model.addAttribute("responsaveis", responsavelService.ativos());
        return "app/documentos/form";
    }
}
