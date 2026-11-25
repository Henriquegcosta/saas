package br.com.vigentra.security;

import br.com.vigentra.domain.enums.PermissaoCodigo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Acesso ao usuário autenticado e ao TENANT atual.
 * O empresaId vem SEMPRE da sessão autenticada no servidor.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UsuarioPrincipal> usuarioOpcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p) return Optional.of(p);
        return Optional.empty();
    }

    public static UsuarioPrincipal usuario() {
        return usuarioOpcional().orElseThrow(() -> new AccessDeniedException("Usuário não autenticado"));
    }

    /** ID da empresa (tenant) do usuário logado. */
    public static Long empresaId() {
        return usuario().getEmpresaId();
    }

    public static boolean tem(PermissaoCodigo permissao) {
        return usuarioOpcional().map(u -> u.tem(permissao)).orElse(false);
    }

    public static void exigir(PermissaoCodigo permissao) {
        if (!tem(permissao)) throw new AccessDeniedException("Permissão necessária: " + permissao.getDescricao());
    }
}
