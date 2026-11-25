package br.com.vigentra.config;

import br.com.vigentra.security.AuthHandlers;
import br.com.vigentra.security.EmailVerificadoFilter;
import br.com.vigentra.security.UsuarioDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Configuração do Spring Security:
 * - login por formulário com BCrypt e bloqueio por tentativas;
 * - "lembrar acesso" com tokens persistidos no banco (rotacionados a cada uso);
 * - CSRF habilitado (padrão) para todos os formulários e chamadas AJAX;
 * - cabeçalhos de segurança (CSP, X-Frame-Options, Referrer-Policy...);
 * - autorização por URL + anotações @PreAuthorize nos controllers.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' data: https://fonts.gstatic.com",
            "img-src 'self' data: blob:",
            "object-src 'none'",
            "frame-src 'self'",
            "frame-ancestors 'self'",
            "base-uri 'self'",
            "form-action 'self'");

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Repositório do contexto de segurança (permite atualizar a sessão após confirmar o e-mail). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UsuarioDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider(uds);
        p.setPasswordEncoder(encoder);
        return p;
    }

    /** Tokens do "lembrar acesso" (tabela persistent_logins); exposto para permitir a revogação. */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        return repo;
    }

    @Bean
    public RememberMeServices rememberMeServices(AppProperties props, UsuarioDetailsService uds,
                                                 PersistentTokenRepository repo) {
        String key = props.rememberMeKey();
        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
            log.warn("APP_REMEMBER_ME_KEY não definida: usando chave aleatória (os acessos lembrados expiram ao reiniciar).");
        }
        PersistentTokenBasedRememberMeServices s = new PersistentTokenBasedRememberMeServices(key, uds, repo);
        s.setParameter("lembrar");
        s.setCookieName("VGREMEMBER");
        s.setTokenValiditySeconds(14 * 24 * 3600);
        return s;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthHandlers handlers, RememberMeServices rememberMe,
                                           SessionRegistry sessionRegistry,
                                           SecurityContextRepository securityContextRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/cadastro", "/recuperar-senha", "/redefinir-senha",
                        "/termos", "/privacidade", "/error", "/error/**").permitAll()
                // links recebidos por e-mail (protegidos por token de uso único ou assinatura HMAC)
                .requestMatchers("/confirmar-email", "/confirmar-email/alteracao", "/convite",
                        "/preferencias", "/preferencias/**").permitAll()
                // webhook oficial da Meta: autenticado pela assinatura X-Hub-Signature-256
                .requestMatchers("/webhooks/whatsapp").permitAll()
                .requestMatchers("/plataforma/**").hasRole("PLATAFORMA")
                .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.svg").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(handlers.sucesso())
                .failureHandler(handlers.falha())
                .permitAll())
            .rememberMe(rm -> rm.rememberMeServices(rememberMe))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .addLogoutHandler(handlers.logout())
                .logoutSuccessUrl("/login?saiu")
                .deleteCookies("VGSESSION", "VGREMEMBER")
                .invalidateHttpSession(true))
            .sessionManagement(s -> s
                .sessionFixation(f -> f.migrateSession())
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredUrl("/login?expirada"))
            .exceptionHandling(e -> e.accessDeniedPage("/error/403"))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(f -> f.sameOrigin())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()")))
            // sem token CSRF: chamados por servidores externos/clientes de e-mail; validados por HMAC
            .csrf(c -> c.ignoringRequestMatchers("/webhooks/whatsapp", "/preferencias/descadastrar"))
            .securityContext(c -> c.securityContextRepository(securityContextRepository))
            .addFilterBefore(handlers.filtroLimiteIp(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new EmailVerificadoFilter(), AuthorizationFilter.class);
        return http.build();
    }
}
