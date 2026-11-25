package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.TipoToken;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioDetailsService;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.conta.ContaService;
import br.com.vigentra.service.conta.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Confirmação de e-mail (cadastro e troca de e-mail).
 * O link do e-mail abre uma página com botão; a confirmação acontece no POST, evitando que
 * robôs de pré-visualização de links (ex.: "links seguros" de provedores) consumam o token.
 */
@Controller
public class ContaController {

    private final ContaService contaService;
    private final TokenService tokens;
    private final UsuarioDetailsService uds;
    private final SecurityContextRepository securityContextRepository;
    private final EmailService emailService;

    public ContaController(ContaService contaService, TokenService tokens, UsuarioDetailsService uds,
                           SecurityContextRepository securityContextRepository, EmailService emailService) {
        this.contaService = contaService;
        this.tokens = tokens;
        this.uds = uds;
        this.securityContextRepository = securityContextRepository;
        this.emailService = emailService;
    }

    // ------------------------------------------------------------------ confirmação do cadastro

    @GetMapping("/confirmar-email")
    public String paginaConfirmacao(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValido", tokens.validar(token, TipoToken.CONFIRMACAO_EMAIL).isPresent());
        model.addAttribute("tipo", "cadastro");
        return "auth/confirmar-email";
    }

    @PostMapping("/confirmar-email")
    public String confirmar(@RequestParam String token, HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        Usuario u;
        try {
            u = contaService.confirmar(token);
        } catch (NegocioException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/confirmar-email";
        }
        if (atualizarSessaoSeForOMesmo(u, req, res)) {
            ra.addFlashAttribute("sucesso", "E-mail confirmado! Boas-vindas ao VIGENTRA.");
            return "redirect:/app/dashboard";
        }
        ra.addFlashAttribute("sucesso", "E-mail confirmado com sucesso! Entre para começar a usar o VIGENTRA.");
        ra.addFlashAttribute("emailCadastrado", u.getEmail());
        return "redirect:/login";
    }

    /** Tela exibida a quem entrou mas ainda não confirmou o e-mail. */
    @GetMapping("/conta/confirmar-email")
    public String pendente(Model model) {
        UsuarioPrincipal u = SecurityUtils.usuario();
        if (u.isEmailVerificado()) return "redirect:/app/dashboard";
        model.addAttribute("email", u.getEmail());
        model.addAttribute("modoDevEmail", emailService.isModoDesenvolvimento());
        model.addAttribute("diretorioDev", emailService.getDiretorioDev());
        return "auth/confirmacao-pendente";
    }

    @PostMapping("/conta/confirmar-email/reenviar")
    public String reenviar(RedirectAttributes ra) {
        try {
            contaService.reenviarConfirmacao();
            ra.addFlashAttribute("sucesso", "Enviamos um novo link de confirmação. O link anterior deixou de valer.");
        } catch (NegocioException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/conta/confirmar-email";
    }

    // ------------------------------------------------------------------ troca de e-mail

    @GetMapping("/confirmar-email/alteracao")
    public String paginaAlteracao(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValido", tokens.validar(token, TipoToken.ALTERACAO_EMAIL).isPresent());
        model.addAttribute("tipo", "alteracao");
        return "auth/confirmar-email";
    }

    @PostMapping("/confirmar-email/alteracao")
    public String confirmarAlteracao(@RequestParam String token, HttpServletRequest req, HttpServletResponse res,
                                     RedirectAttributes ra) {
        Usuario u;
        try {
            u = contaService.confirmarAlteracaoEmail(token);
        } catch (NegocioException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/confirmar-email/alteracao";
        }
        if (atualizarSessaoSeForOMesmo(u, req, res)) {
            ra.addFlashAttribute("sucesso", "Novo e-mail confirmado. Use " + u.getEmail() + " para entrar a partir de agora.");
            ra.addAttribute("aba", "perfil");
            return "redirect:/app/configuracoes";
        }
        ra.addFlashAttribute("sucesso", "Novo e-mail confirmado. Entre usando " + u.getEmail() + ".");
        ra.addFlashAttribute("emailCadastrado", u.getEmail());
        return "redirect:/login";
    }

    /**
     * Se quem confirmou já está logado neste navegador, atualiza a sessão com os dados novos
     * (e-mail verificado / novo login), sem exigir novo login.
     */
    private boolean atualizarSessaoSeForOMesmo(Usuario u, HttpServletRequest req, HttpServletResponse res) {
        var atual = SecurityUtils.usuarioOpcional();
        if (atual.isEmpty() || !atual.get().getId().equals(u.getId())) return false;
        UsuarioPrincipal novo = uds.loadUserByUsername(u.getEmail());
        novo.eraseCredentials(); // montado aqui, fora do ProviderManager: o hash não deve ir para a sessão
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(novo, null, novo.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        securityContextRepository.saveContext(ctx, req, res);
        return true;
    }
}
