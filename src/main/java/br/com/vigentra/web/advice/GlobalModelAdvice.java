package br.com.vigentra.web.advice;

import br.com.vigentra.domain.model.Notificacao;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.NotificacaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** Atributos disponíveis em todos os templates (cabeçalho, menu, sino de notificações). */
@ControllerAdvice
public class GlobalModelAdvice {

    private final NotificacaoService notificacaoService;
    private final EmailService emailService;

    public GlobalModelAdvice(NotificacaoService notificacaoService, EmailService emailService) {
        this.notificacaoService = notificacaoService;
        this.emailService = emailService;
    }

    @ModelAttribute("caminho")
    public String caminho(HttpServletRequest req) {
        return req.getRequestURI().substring(req.getContextPath().length());
    }

    @ModelAttribute("usuarioLogado")
    public UsuarioPrincipal usuarioLogado() {
        return SecurityUtils.usuarioOpcional().orElse(null);
    }

    @ModelAttribute("modoDevEmail")
    public boolean modoDevEmail() {
        return emailService.isModoDesenvolvimento();
    }

    @ModelAttribute("notificacoesNaoLidas")
    public long notificacoesNaoLidas(HttpServletRequest req) {
        return areaLogada(req) ? notificacaoService.naoLidas() : 0;
    }

    @ModelAttribute("notificacoesRecentes")
    public List<Notificacao> notificacoesRecentes(HttpServletRequest req) {
        return areaLogada(req) ? notificacaoService.recentesNaoLidas(6) : List.of();
    }

    private boolean areaLogada(HttpServletRequest req) {
        return SecurityUtils.usuarioOpcional().isPresent()
                && req.getRequestURI().startsWith(req.getContextPath() + "/app")
                && !req.getRequestURI().startsWith(req.getContextPath() + "/app/api");
    }
}
