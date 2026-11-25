package br.com.vigentra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Contas com e-mail ainda NÃO confirmado podem entrar, mas só enxergam a tela
 * "Confirme seu e-mail" (com reenvio). Toda a área protegida (/app, /plataforma) fica bloqueada.
 */
public class EmailVerificadoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String caminho = req.getRequestURI().substring(req.getContextPath().length());
        boolean protegido = caminho.startsWith("/app") || caminho.startsWith("/plataforma");
        if (protegido && SecurityUtils.usuarioOpcional().map(u -> !u.isEmailVerificado()).orElse(false)) {
            if (caminho.startsWith("/app/api")) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "E-mail não confirmado");
            } else {
                res.sendRedirect(req.getContextPath() + "/conta/confirmar-email");
            }
            return;
        }
        chain.doFilter(req, res);
    }
}
