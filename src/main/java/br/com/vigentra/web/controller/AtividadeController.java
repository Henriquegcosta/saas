package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.UsuarioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Log de atividades (auditoria) com pesquisa e filtros. */
@Controller
public class AtividadeController {

    private final AuditoriaService auditoria;
    private final UsuarioService usuarioService;
    private final ConfiguracaoService configuracaoService;

    public AtividadeController(AuditoriaService auditoria, UsuarioService usuarioService, ConfiguracaoService configuracaoService) {
        this.auditoria = auditoria;
        this.usuarioService = usuarioService;
        this.configuracaoService = configuracaoService;
    }

    @GetMapping("/app/atividades")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public String listar(@RequestParam(required = false) String q,
                         @RequestParam(required = false) AcaoLog acao,
                         @RequestParam(required = false) Long usuarioId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        int tamanho = Math.max(configuracaoService.atual().getItensPorPagina(), 20);
        model.addAttribute("pagina", auditoria.buscar(q, acao, usuarioId, de, ate, pagina, tamanho));
        model.addAttribute("acoes", AcaoLog.values());
        model.addAttribute("usuarios", usuarioService.todos());
        model.addAttribute("q", q);
        model.addAttribute("acao", acao);
        model.addAttribute("usuarioId", usuarioId);
        model.addAttribute("de", de);
        model.addAttribute("ate", ate);
        return "app/atividades";
    }
}
