package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.conta.ConviteService;
import br.com.vigentra.web.dto.ConviteForm;
import br.com.vigentra.web.dto.UsuarioForm;
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

import java.time.Clock;
import java.time.LocalDateTime;

/** Área administrativa de usuários e convites. As regras de hierarquia ficam nos serviços. */
@Controller
@RequestMapping("/app/usuarios")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UsuarioController {

    private final UsuarioService service;
    private final ConviteService conviteService;
    private final ConfiguracaoService configuracaoService;
    private final EmailService emailService;
    private final Clock clock;

    public UsuarioController(UsuarioService service, ConviteService conviteService, ConfiguracaoService configuracaoService,
                             EmailService emailService, Clock clock) {
        this.service = service;
        this.conviteService = conviteService;
        this.configuracaoService = configuracaoService;
        this.emailService = emailService;
        this.clock = clock;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String q, @RequestParam(required = false) PerfilCodigo perfil,
                         @RequestParam(required = false) Boolean ativo, @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pagina", service.buscar(q, perfil, ativo, pagina, configuracaoService.atual().getItensPorPagina()));
        model.addAttribute("convites", conviteService.abertos());
        model.addAttribute("agora", LocalDateTime.now(clock));
        model.addAttribute("q", q);
        model.addAttribute("perfil", perfil);
        model.addAttribute("ativo", ativo);
        model.addAttribute("perfis", PerfilCodigo.values());
        model.addAttribute("svc", service);
        return "app/usuarios/lista";
    }

    // ---------------------------------------------------------------- convites

    @GetMapping({"/novo", "/convidar"})
    public String convidar(Model model) {
        return formConvite(new ConviteForm(), model);
    }

    @PostMapping("/convites")
    public String enviarConvite(@Valid @ModelAttribute("form") ConviteForm form, BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) return formConvite(form, model);
        try {
            conviteService.convidar(form);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return formConvite(form, model);
        }
        ra.addFlashAttribute("sucesso", "Convite enviado para " + form.getEmail().trim().toLowerCase()
                + ". O link vale por 7 dias e só pode ser usado uma vez." + modoDev());
        return "redirect:/app/usuarios";
    }

    @PostMapping("/convites/{id}/reenviar")
    public String reenviarConvite(@PathVariable Long id, RedirectAttributes ra) {
        var c = conviteService.reenviar(id);
        ra.addFlashAttribute("sucesso", "Novo convite enviado para " + c.convite().getEmail() + ". O link anterior deixou de valer." + modoDev());
        return "redirect:/app/usuarios";
    }

    @PostMapping("/convites/{id}/cancelar")
    public String cancelarConvite(@PathVariable Long id, RedirectAttributes ra) {
        conviteService.cancelar(id);
        ra.addFlashAttribute("sucesso", "Convite cancelado. O link enviado não funciona mais.");
        return "redirect:/app/usuarios";
    }

    // ---------------------------------------------------------------- usuários existentes

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Usuario u = service.buscar(id);
        if (!service.podeGerenciar(u)) {
            ra.addFlashAttribute("erro", "Você não tem permissão para editar este usuário.");
            return "redirect:/app/usuarios";
        }
        model.addAttribute("usuario", u);
        return form(UsuarioForm.de(u), model);
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("form") UsuarioForm form, BindingResult br, Model model, RedirectAttributes ra) {
        if (form.getId() == null) return "redirect:/app/usuarios/convidar";
        Usuario u = service.buscar(form.getId());
        model.addAttribute("usuario", u);
        if (br.hasErrors()) return form(form, model);
        try {
            service.salvar(form);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return form(form, model);
        }
        ra.addFlashAttribute("sucesso", "Usuário atualizado.");
        return "redirect:/app/usuarios";
    }

    @PostMapping("/{id}/redefinir-senha")
    public String redefinirSenha(@PathVariable Long id, RedirectAttributes ra) {
        service.enviarRedefinicaoSenha(id);
        ra.addFlashAttribute("sucesso", "Link de redefinição de senha enviado ao usuário (válido por 30 minutos)." + modoDev());
        return "redirect:/app/usuarios/" + id + "/editar";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, RedirectAttributes ra) {
        service.alternarAtivo(id);
        ra.addFlashAttribute("sucesso", "Situação do usuário atualizada.");
        return "redirect:/app/usuarios";
    }

    @PostMapping("/{id}/excluir")
    @PreAuthorize("hasRole('ADMIN')")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        service.excluir(id);
        ra.addFlashAttribute("sucesso", "Usuário excluído.");
        return "redirect:/app/usuarios";
    }

    private String modoDev() {
        return emailService.isModoDesenvolvimento() ? " (Modo desenvolvimento: o e-mail foi gravado em disco, não enviado.)" : "";
    }

    private String formConvite(ConviteForm f, Model model) {
        model.addAttribute("form", f);
        model.addAttribute("perfis", service.perfisAtribuiveis());
        return "app/usuarios/convite";
    }

    private String form(UsuarioForm f, Model model) {
        model.addAttribute("form", f);
        model.addAttribute("perfis", service.perfisAtribuiveis());
        return "app/usuarios/form";
    }
}
