package br.com.vigentra.web.advice;

import br.com.vigentra.service.NegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Converte erros de regra de negócio em mensagens amigáveis, voltando à página de origem.
 * Erros de acesso (403) e recurso inexistente (404) seguem para as páginas de erro.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NegocioException.class)
    public String negocio(NegocioException e, HttpServletRequest req, RedirectAttributes ra) {
        ra.addFlashAttribute("erro", e.getMessage());
        return "redirect:" + voltar(req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadGrande(HttpServletRequest req, RedirectAttributes ra) {
        ra.addFlashAttribute("erro", "O arquivo excede o tamanho máximo permitido (10 MB).");
        return "redirect:" + voltar(req);
    }

    /** Usa apenas o caminho do Referer (nunca o host), evitando "open redirect". */
    static String voltar(HttpServletRequest req) {
        String ref = req.getHeader("Referer");
        if (ref != null) {
            try {
                URI u = URI.create(ref);
                String path = u.getRawPath();
                if (path != null && path.startsWith("/") && !path.startsWith("//")) {
                    String ctx = req.getContextPath();
                    if (!ctx.isEmpty() && path.startsWith(ctx)) path = path.substring(ctx.length());
                    return path + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
                }
            } catch (IllegalArgumentException ex) {
                log.debug("Referer inválido: {}", ref);
            }
        }
        return "/app/dashboard";
    }
}
