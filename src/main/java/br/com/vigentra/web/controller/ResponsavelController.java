package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.comunicacao.CanaisService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.ResponsavelService;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.web.dto.ResponsavelForm;
import jakarta.validation.Valid;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Gerenciamento dos responsáveis pelos documentos. */
@Controller
@RequestMapping("/app/responsaveis")
@PreAuthorize("hasAuthority('RESP_MANAGE')")
public class ResponsavelController {

    private final ResponsavelService service;
    private final UsuarioService usuarioService;
    private final ConfiguracaoService configuracaoService;
    private final CanaisService canaisService;

    public ResponsavelController(ResponsavelService service, UsuarioService usuarioService, ConfiguracaoService configuracaoService,
                                 CanaisService canaisService) {
        this.service = service;
        this.usuarioService = usuarioService;
        this.configuracaoService = configuracaoService;
        this.canaisService = canaisService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pagina", service.buscar(q, pagina, configuracaoService.atual().getItensPorPagina()));
        model.addAttribute("q", q);
        model.addAttribute("svc", service);
        return "app/responsaveis/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return form(new ResponsavelForm(), model);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        var r = service.buscar(id);
        model.addAttribute("resp", r);
        return form(ResponsavelForm.de(r), model);
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("form") ResponsavelForm form, BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) return form(form, model);
        try {
            service.salvar(form);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return form(form, model);
        }
        ra.addFlashAttribute("sucesso", "Responsável salvo com sucesso.");
        return "redirect:/app/responsaveis";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, RedirectAttributes ra) {
        service.alternarAtivo(id);
        ra.addFlashAttribute("sucesso", "Situação do responsável atualizada.");
        return "redirect:/app/responsaveis";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        service.excluir(id);
        ra.addFlashAttribute("sucesso", "Responsável excluído.");
        return "redirect:/app/responsaveis";
    }

    private String form(ResponsavelForm f, Model model) {
        model.addAttribute("form", f);
        model.addAttribute("usuarios", usuarioService.todos());
        model.addAttribute("statusWhatsapp", canaisService.status(CanalEnvio.WHATSAPP));
        model.addAttribute("statusSms", canaisService.status(CanalEnvio.SMS));
        if (!model.containsAttribute("resp")) model.addAttribute("resp", null);
        return "app/responsaveis/form";
    }
}
