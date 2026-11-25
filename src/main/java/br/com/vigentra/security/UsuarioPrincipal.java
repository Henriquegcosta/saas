package br.com.vigentra.security;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Usuário autenticado guardado na sessão. Carrega o ID da EMPRESA (tenant):
 * é daqui que todas as camadas obtêm o tenant - nunca de um parâmetro enviado pelo navegador.
 * Implementa {@link CredentialsContainer}: após a autenticação o Spring Security (ProviderManager)
 * chama {@link #eraseCredentials()}, e o hash da senha não fica guardado na sessão.
 */
public class UsuarioPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final Long empresaId;
    private final String empresaNome;
    private final boolean empresaDemo;
    private final String nome;
    private final String email;
    /** Usado só durante a autenticação; apagado em {@link #eraseCredentials()}. */
    private String senhaHash;
    private final PerfilCodigo perfil;
    private final boolean ativo;
    private final LocalDateTime bloqueadoAte;
    private final Set<PermissaoCodigo> permissoes;
    private final Set<GrantedAuthority> authorities;
    private final boolean emailVerificado;
    /** Operador da plataforma VIGENTRA (APP_PLATFORM_ADMINS): acesso ao painel global de comunicação. */
    private final boolean plataforma;

    public UsuarioPrincipal(Long id, Long empresaId, String empresaNome, boolean empresaDemo, String nome, String email,
                            String senhaHash, PerfilCodigo perfil, boolean ativo, LocalDateTime bloqueadoAte,
                            Set<PermissaoCodigo> permissoes, boolean emailVerificado, boolean plataforma) {
        this.id = id;
        this.empresaId = empresaId;
        this.empresaNome = empresaNome;
        this.empresaDemo = empresaDemo;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.ativo = ativo;
        this.bloqueadoAte = bloqueadoAte;
        this.permissoes = Set.copyOf(permissoes);
        this.emailVerificado = emailVerificado;
        this.plataforma = plataforma;
        Set<GrantedAuthority> auth = new HashSet<>();
        auth.add(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
        if (plataforma) auth.add(new SimpleGrantedAuthority("ROLE_PLATAFORMA"));
        permissoes.forEach(p -> auth.add(new SimpleGrantedAuthority(p.name())));
        this.authorities = Set.copyOf(auth);
    }

    public boolean tem(PermissaoCodigo p) { return permissoes.contains(p); }
    public boolean isAdmin() { return perfil == PerfilCodigo.ADMIN; }
    public boolean isEmailVerificado() { return emailVerificado; }
    public boolean isPlataforma() { return plataforma; }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getEmpresaNome() { return empresaNome; }
    public boolean isEmpresaDemo() { return empresaDemo; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public PerfilCodigo getPerfil() { return perfil; }
    public Set<PermissaoCodigo> getPermissoes() { return permissoes; }

    public String getPrimeiroNome() {
        return nome == null ? "" : nome.trim().split("\\s+")[0];
    }

    public String getIniciais() {
        if (nome == null || nome.isBlank()) return "?";
        String[] p = nome.trim().split("\\s+");
        String i = p[0].substring(0, 1);
        if (p.length > 1) i += p[p.length - 1].substring(0, 1);
        return i.toUpperCase();
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return senhaHash; }
    @Override public void eraseCredentials() { senhaHash = null; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return ativo; }
    @Override public boolean isAccountNonLocked() {
        return bloqueadoAte == null || LocalDateTime.now().isAfter(bloqueadoAte);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UsuarioPrincipal u && Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
