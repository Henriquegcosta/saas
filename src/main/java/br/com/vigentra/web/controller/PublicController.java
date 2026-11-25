package br.com.vigentra.web.controller;

import br.com.vigentra.security.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Páginas públicas: landing page, termos de uso, política de privacidade e erro 403. */
@Controller
public class PublicController {

    @GetMapping("/")
    public String index() {
        return "public/index";
    }

    @GetMapping("/termos")
    public String termos() {
        return "public/termos";
    }

    @GetMapping("/privacidade")
    public String privacidade() {
        return "public/privacidade";
    }

    @GetMapping("/error/403")
    public String acessoNegado() {
        return SecurityUtils.usuarioOpcional().isPresent() ? "error/403" : "redirect:/login";
    }

    @GetMapping("/app")
    public String app() {
        return "redirect:/app/dashboard";
    }
}
