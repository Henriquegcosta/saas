package br.com.vigentra.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class RequestUtils {

    private RequestUtils() {}

    public static Optional<HttpServletRequest> requestAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }

    /**
     * IP do cliente. Atrás do Nginx, o Tomcat (server.forward-headers-strategy=native)
     * já substitui o remoteAddr pelo valor de X-Forwarded-For vindo do proxy confiável.
     */
    public static String ipCliente() {
        return requestAtual().map(HttpServletRequest::getRemoteAddr).orElse(null);
    }
}
