package br.com.vigentra.security;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.service.AuditoriaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Handlers de login/logout: auditoria, contagem de tentativas e redirecionamentos. */
@Component
public class AuthHandlers {

    private final LoginAttemptService attempts;
    private final AuditoriaService auditoria;
    private final UsuarioRepository usuarioRepo;

    public AuthHandlers(LoginAttemptService attempts, AuditoriaService auditoria, UsuarioRepository usuarioRepo) {
        this.attempts = attempts;
        this.auditoria = auditoria;
        this.usuarioRepo = usuarioRepo;
    }

    /** Sucesso: zera tentativas, registra o login e volta para a página solicitada (ou dashboard). */
    public SavedRequestAwareAuthenticationSuccessHandler sucesso() {
        var handler = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
                    throws ServletException, IOException {
                if (auth.getPrincipal() instanceof UsuarioPrincipal u) {
                    attempts.registrarSucesso(u.getId(), req.getRemoteAddr());
                    auditoria.registrarPara(u.getEmpresaId(), u.getId(), u.getNome(), u.getEmail(),
                            AcaoLog.LOGIN, "Usuario", u.getId(), "Login realizado com sucesso", true);
                }
                super.onAuthenticationSuccess(req, res, auth);
            }
        };
        handler.setDefaultTargetUrl("/app/dashboard");
        return handler;
    }

    /** Falha: incrementa contadores, audita e redireciona com o motivo (sem revelar se o e-mail existe). */
    public AuthenticationFailureHandler falha() {
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) -> {
            String email = req.getParameter("username");
            String ip = req.getRemoteAddr();
            String motivo;
            if (ex instanceof LockedException) {
                motivo = "bloqueado";
            } else if (ex instanceof DisabledException) {
                motivo = "inativo";
            } else {
                boolean bloqueou = attempts.registrarFalha(email, ip);
                motivo = bloqueou ? "bloqueado" : "credenciais";
            }
            usuarioRepo.findByEmailComEmpresa(email == null ? "" : email.trim()).ifPresent(u ->
                    auditoria.registrarPara(u.getEmpresa().getId(), u.getId(), u.getNome(), u.getEmail(),
                            AcaoLog.LOGIN_FALHA, "Usuario", u.getId(),
                            "Tentativa de login recusada (" + motivo + ") a partir do IP " + ip, false));
            res.sendRedirect(req.getContextPath() + "/login?erro=" + motivo);
        };
    }

    /** Registra o logout antes de a sessão ser destruída. */
    public LogoutHandler logout() {
        return (req, res, auth) -> {
            if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal u) {
                auditoria.registrarPara(u.getEmpresaId(), u.getId(), u.getNome(), u.getEmail(),
                        AcaoLog.LOGOUT, "Usuario", u.getId(), "Logout realizado", true);
            }
        };
    }

    /** Filtro que recusa tentativas de login de IPs com excesso de falhas recentes. */
    public OncePerRequestFilter filtroLimiteIp() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                if ("POST".equals(req.getMethod()) && "/login".equals(req.getRequestURI().substring(req.getContextPath().length()))
                        && attempts.ipBloqueado(req.getRemoteAddr())) {
                    res.sendRedirect(req.getContextPath() + "/login?erro=ip");
                    return;
                }
                chain.doFilter(req, res);
            }
        };
    }
}
