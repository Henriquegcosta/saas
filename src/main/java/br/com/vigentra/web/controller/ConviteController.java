package br.com.vigentra.web.controller;

import br.com.vigentra.domain.model.Convite;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.conta.ConviteService;
import br.com.vigentra.web.dto.AceiteConviteForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/** Página pública de aceite de convite (link recebido por e-mail). */
@Controller
public class ConviteController {

    private final ConviteService conviteService;

    public ConviteController(ConviteService conviteService) {
        this.conviteService = conviteService;
    }

    @GetMapping("/convite")
    public String pagina(@RequestParam(required = false) String token, Model model) {
        if (SecurityUtils.usuarioOpcional().isPresent()) {
            model.addAttribute("logado", true);
        }
        AceiteConviteForm f = new AceiteConviteForm();
        f.setToken(token);
        conviteService.validar(token).ifPresent(c -> f.setNome(c.getNome()));
        return pagina(f, model);
    }

    @PostMapping("/convite")
    public String aceitar(@Valid @ModelAttribute("form") AceiteConviteForm form, BindingResult br,
                          Model model, RedirectAttributes ra) {
        if (form.getSenha() != null && !form.getSenha().equals(form.getConfirmacaoSenha())) {
            br.rejectValue("confirmacaoSenha", "diferente", "As senhas não conferem.");
        }
        if (br.hasErrors()) return pagina(form, model);
        Usuario u;
        try {
            u = conviteService.aceitar(form);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return pagina(form, model);
        }
        ra.addFlashAttribute("sucesso", "Conta criada! Você já faz parte de " + u.getEmpresa().getNome() + ". Entre com seu e-mail e senha.");
        ra.addFlashAttribute("emailCadastrado", u.getEmail());
        return "redirect:/login";
    }

    private String pagina(AceiteConviteForm f, Model model) {
        Optional<Convite> c = conviteService.validar(f.getToken());
        model.addAttribute("form", f);
        model.addAttribute("convite", c.orElse(null));
        return "auth/convite";
    }
}
