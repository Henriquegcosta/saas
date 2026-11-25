package br.com.vigentra.security;

import br.com.vigentra.domain.enums.PerfilCodigo;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Encerra sessões ativas quando um usuário é desativado ou tem permissões alteradas,
 * forçando novo login com os direitos atualizados. Também revoga os tokens do
 * "lembrar acesso" (cookie VGREMEMBER) quando a senha muda.
 */
@Service
public class SessaoService {

    private final SessionRegistry sessionRegistry;
    private final PersistentTokenRepository tokensLembrarAcesso;

    public SessaoService(SessionRegistry sessionRegistry, PersistentTokenRepository tokensLembrarAcesso) {
        this.sessionRegistry = sessionRegistry;
        this.tokensLembrarAcesso = tokensLembrarAcesso;
    }

    public void expirarUsuario(Long usuarioId) {
        expirar(p -> p.getId().equals(usuarioId));
    }

    public void expirarPerfil(Long empresaId, PerfilCodigo perfil, Long exceto) {
        expirar(p -> p.getEmpresaId().equals(empresaId) && p.getPerfil() == perfil && !p.getId().equals(exceto));
    }

    /**
     * Encerra as sessões de vários usuários (ex.: todos os usuários de uma empresa desativada).
     * Os IDs devem vir do banco: o registro de sessões guarda o objeto do PRIMEIRO login do usuário,
     * então filtrar pelos dados dele (empresa, perfil) pode usar informação desatualizada.
     */
    public void expirarUsuarios(Collection<Long> usuarioIds) {
        Set<Long> ids = Set.copyOf(usuarioIds);
        if (!ids.isEmpty()) expirar(p -> ids.contains(p.getId()));
    }

    /**
     * Apaga todos os tokens de "lembrar acesso" do usuário (todos os dispositivos).
     * Sem isso, um cookie VGREMEMBER emitido antes da troca de senha continuaria entrando por até 14 dias.
     */
    public void revogarLembrarAcesso(String email) {
        if (email != null) tokensLembrarAcesso.removeUserTokens(email);
    }

    private void expirar(Predicate<UsuarioPrincipal> criterio) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UsuarioPrincipal p && criterio.test(p)) {
                sessionRegistry.getAllSessions(p, false).forEach(SessionInformation::expireNow);
            }
        }
    }
}
