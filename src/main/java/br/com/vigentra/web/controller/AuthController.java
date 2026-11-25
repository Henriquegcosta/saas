package br.com.vigentra.web.controller;

import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.EmpresaService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.RecuperacaoSenhaService;
import br.com.vigentra.web.dto.AlterarSenhaForm;
import br.com.vigentra.web.dto.RegistroForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Login, cadastro de empresa e recuperação de senha. O POST /login é tratado pelo Spring Security. */
@Controller
public class AuthController {

    private final EmpresaService empresaService;
    private final RecuperacaoSenhaService recuperacaoService;
    private final EmailService emailService;
    private final UsuarioRepository usuarioRepo;

    public AuthController(EmpresaService empresaService, RecuperacaoSenhaService recuperacaoService,
                          EmailService emailService, UsuarioRepository usuarioRepo) {
        this.empresaService = empresaService;
        this.recuperacaoService = recuperacaoService;
        this.emailService = emailService;
        this.usuarioRepo = usuarioRepo;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String erro, Model model) {
        if (SecurityUtils.usuarioOpcional().isPresent()) return "redirect:/app/dashboard";
        // só exibe as contas de demonstração se elas realmente existirem no banco
        model.addAttribute("demoDisponivel", usuarioRepo.existsByEmailIgnoreCase("admin@vidaplena.demo"));
        if (erro != null) {
            model.addAttribute("mensagemErro", switch (erro) {
                case "bloqueado" -> "Conta bloqueada temporariamente após várias tentativas sem sucesso. Tente novamente em 15 minutos ou redefina sua senha.";
                case "inativo" -> "Este usuário está desativado. Procure o administrador da sua empresa.";
                case "ip" -> "Muitas tentativas de login a partir da sua rede. Aguarde alguns minutos.";
                default -> "E-mail ou senha inválidos.";
            });
        }
        return "auth/login";
    }

    @GetMapping("/cadastro")
    public String cadastro(Model model) {
        if (SecurityUtils.usuarioOpcional().isPresent()) return "redirect:/app/dashboard";
        model.addAttribute("form", new RegistroForm());
        return "auth/cadastro";
    }

    @PostMapping("/cadastro")
    public String cadastrar(@Valid @ModelAttribute("form") RegistroForm form, BindingResult br, RedirectAttributes ra) {
        if (form.getSenha() != null && !form.getSenha().equals(form.getConfirmacaoSenha())) {
            br.rejectValue("confirmacaoSenha", "senha.diferente", "As senhas não conferem.");
        }
        if (br.hasErrors()) return "auth/cadastro";
        try {
            empresaService.registrar(form);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return "auth/cadastro";
        }
        ra.addFlashAttribute("sucesso", "Empresa cadastrada! Enviamos um link de confirmação para "
                + form.getEmail().trim().toLowerCase() + ". Confirme o e-mail para acessar o sistema.");
        if (emailService.isModoDesenvolvimento()) {
            ra.addFlashAttribute("info", "Modo desenvolvimento: o e-mail não foi enviado; ele foi gravado em "
                    + emailService.getDiretorioDev() + ". Abra o arquivo .html mais recente para clicar no link.");
        }
        ra.addFlashAttribute("emailCadastrado", form.getEmail().trim().toLowerCase());
        return "redirect:/login";
    }

    @GetMapping("/recuperar-senha")
    public String recuperar() {
        return "auth/recuperar-senha";
    }

    @PostMapping("/recuperar-senha")
    public String solicitarRecuperacao(@RequestParam String email, RedirectAttributes ra) {
        try {
            recuperacaoService.solicitar(email);
        } catch (NegocioException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/recuperar-senha";
        }
        // resposta sempre igual: não revela se o e-mail existe
        ra.addFlashAttribute("enviado", true);
        if (emailService.isModoDesenvolvimento()) {
            ra.addFlashAttribute("info", "Modo desenvolvimento: nenhum e-mail é enviado. Se o endereço estiver cadastrado, "
                    + "a mensagem com o link foi gravada em " + emailService.getDiretorioDev());
        }
        return "redirect:/recuperar-senha";
    }

    @GetMapping("/redefinir-senha")
    public String redefinir(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("tokenValido", recuperacaoService.tokenValido(token));
        AlterarSenhaForm f = new AlterarSenhaForm();
        f.setToken(token);
        model.addAttribute("form", f);
        return "auth/redefinir-senha";
    }

    @PostMapping("/redefinir-senha")
    public String salvarNovaSenha(@Valid @ModelAttribute("form") AlterarSenhaForm form, BindingResult br,
                                  Model model, RedirectAttributes ra) {
        model.addAttribute("tokenValido", recuperacaoService.tokenValido(form.getToken()));
        if (form.getNovaSenha() != null && !form.getNovaSenha().equals(form.getConfirmacao())) {
            br.rejectValue("confirmacao", "senha.diferente", "As senhas não conferem.");
        }
        if (br.hasErrors()) return "auth/redefinir-senha";
        try {
            recuperacaoService.redefinir(form.getToken(), form.getNovaSenha(), form.getConfirmacao());
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return "auth/redefinir-senha";
        }
        ra.addFlashAttribute("sucesso", "Senha redefinida com sucesso. Por segurança, enviamos um aviso para o seu e-mail. "
                + "Faça login com a nova senha.");
        return "redirect:/login";
    }
}
