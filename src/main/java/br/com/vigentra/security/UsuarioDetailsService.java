package br.com.vigentra.security;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.service.PermissaoService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carrega o usuário (pelo e-mail) e suas permissões para o Spring Security. */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepo;
    private final PermissaoService permissaoService;
    private final AppProperties props;

    public UsuarioDetailsService(UsuarioRepository usuarioRepo, PermissaoService permissaoService, AppProperties props) {
        this.usuarioRepo = usuarioRepo;
        this.permissaoService = permissaoService;
        this.props = props;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioPrincipal loadUserByUsername(String email) {
        Usuario u = usuarioRepo.findByEmailComEmpresa(email == null ? "" : email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
        return paraPrincipal(u);
    }

    public UsuarioPrincipal paraPrincipal(Usuario u) {
        boolean ativo = u.isAtivo() && u.getEmpresa().isAtivo();
        return new UsuarioPrincipal(u.getId(), u.getEmpresa().getId(), u.getEmpresa().getNome(), u.getEmpresa().isDemo(),
                u.getNome(), u.getEmail(), u.getSenhaHash(), u.getPerfil().getCodigo(), ativo, u.getBloqueadoAte(),
                permissaoService.permissoesDe(u.getEmpresa().getId(), u.getPerfil()),
                u.isEmailVerificado(), props.operadoresPlataforma().contains(u.getEmail().toLowerCase()));
    }
}
