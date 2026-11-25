package br.com.vigentra;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.TipoToken;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.security.LoginAttemptService;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.EmpresaService;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.service.conta.TokenService;
import br.com.vigentra.web.dto.AlterarSenhaForm;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Sessões: revogação do \"lembrar acesso\", empresa desativada e credenciais fora da sessão")
class SessaoSegurancaTest extends IntegracaoBase {

    private static final String LEMBRAR = "VGREMEMBER";

    @Autowired UsuarioService usuarioService;
    @Autowired EmpresaService empresaService;
    @Autowired EmpresaRepository empresaRepo;
    @Autowired TokenService tokenService;
    @Autowired LoginAttemptService attempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        attempts.limpar();
    }

    // ------------------------------------------------------------------ apoio

    /** Login pelo formulário, como o navegador faz (opcionalmente marcando "lembrar acesso"). */
    private MvcResult entrar(Usuario u, boolean lembrar) throws Exception {
        var req = post("/login").with(csrf()).param("username", u.getEmail()).param("password", TestSupport.SENHA);
        if (lembrar) req = req.param("lembrar", "true");
        return mvc.perform(req).andExpect(redirectedUrl("/app/dashboard")).andReturn();
    }

    private static MockHttpSession sessao(MvcResult r) {
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private static UsuarioPrincipal principalDaSessao(MockHttpSession s) {
        SecurityContext ctx = (SecurityContext) s.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return (UsuarioPrincipal) ctx.getAuthentication().getPrincipal();
    }

    private int tokensLembrar(String email) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username = ?", Integer.class, email);
        return n == null ? 0 : n;
    }

    /** Usa o cookie (sem sessão) e devolve o cookie renovado - o token é rotacionado a cada uso. */
    private Cookie usarLembrarAcesso(Cookie cookie) throws Exception {
        MvcResult r = mvc.perform(get("/app/dashboard").cookie(cookie)).andExpect(status().isOk()).andReturn();
        Cookie renovado = r.getResponse().getCookie(LEMBRAR);
        assertThat(renovado).isNotNull();
        return renovado;
    }

    // ------------------------------------------------------------------ 1. lembrar acesso x senha

    @Test
    @DisplayName("Alterar a própria senha revoga o \"lembrar acesso\" de todos os dispositivos")
    void alterarSenhaRevogaLembrarAcesso() throws Exception {
        Usuario u = ts.novaEmpresa("Lembrar Troca");
        Cookie cookie = entrar(u, true).getResponse().getCookie(LEMBRAR);
        assertThat(cookie).isNotNull();
        assertThat(tokensLembrar(u.getEmail())).isEqualTo(1);
        cookie = usarLembrarAcesso(cookie);              // controle: o cookie funciona antes da troca

        ts.logar(u);
        AlterarSenhaForm f = new AlterarSenhaForm();
        f.setSenhaAtual(TestSupport.SENHA);
        f.setNovaSenha("NovaSenha@2026");
        f.setConfirmacao("NovaSenha@2026");
        usuarioService.alterarPropriaSenha(f);
        ts.deslogar();

        assertThat(tokensLembrar(u.getEmail())).isZero();
        mvc.perform(get("/app/dashboard").cookie(cookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Redefinir a senha pelo link encerra a sessão e revoga o \"lembrar acesso\"")
    void redefinirSenhaRevogaLembrarAcesso() throws Exception {
        Usuario u = ts.novaEmpresa("Lembrar Redefinição");
        MvcResult login = entrar(u, true);
        MockHttpSession sessao = sessao(login);
        Cookie cookie = usarLembrarAcesso(login.getResponse().getCookie(LEMBRAR));
        mvc.perform(get("/app/dashboard").session(sessao)).andExpect(status().isOk());

        String token = tokenService.emitir(u, TipoToken.RECUPERACAO_SENHA, Duration.ofMinutes(30));
        mvc.perform(post("/redefinir-senha").with(csrf()).param("token", token)
                        .param("novaSenha", "Redefinida@2026").param("confirmacao", "Redefinida@2026"))
                .andExpect(redirectedUrl("/login"));

        assertThat(tokensLembrar(u.getEmail())).isZero();
        mvc.perform(get("/app/dashboard").session(sessao)).andExpect(redirectedUrl("/login?expirada"));
        mvc.perform(get("/app/dashboard").cookie(cookie)).andExpect(redirectedUrlPattern("**/login"));
    }

    // ------------------------------------------------------------------ 2. empresa desativada

    @Test
    @DisplayName("Desativar a empresa encerra as sessões de todos os seus usuários (e só deles)")
    void empresaDesativadaEncerraSessoes() throws Exception {
        Usuario admin = ts.novaEmpresa("Desativada");
        ts.logar(admin);
        Usuario colega = ts.usuario(PerfilCodigo.USUARIO);
        ts.deslogar();
        Usuario outraEmpresa = ts.novaEmpresa("Continua Ativa");

        MvcResult loginAdmin = entrar(admin, true);
        MockHttpSession sAdmin = sessao(loginAdmin);
        Cookie cookieAdmin = usarLembrarAcesso(loginAdmin.getResponse().getCookie(LEMBRAR));
        MockHttpSession sColega = sessao(entrar(colega, false));
        MockHttpSession sOutra = sessao(entrar(outraEmpresa, false));
        mvc.perform(get("/app/dashboard").session(sAdmin)).andExpect(status().isOk());
        mvc.perform(get("/app/dashboard").session(sColega)).andExpect(status().isOk());

        empresaService.desativar(admin.getEmpresa().getId());

        assertThat(empresaRepo.findById(admin.getEmpresa().getId()).orElseThrow().isAtivo()).isFalse();
        mvc.perform(get("/app/dashboard").session(sAdmin)).andExpect(redirectedUrl("/login?expirada"));
        mvc.perform(get("/app/dashboard").session(sColega)).andExpect(redirectedUrl("/login?expirada"));
        // o "lembrar acesso" não reabre a sessão: a conta passa a ser considerada inativa
        mvc.perform(get("/app/dashboard").cookie(cookieAdmin)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(formLogin("/login").user(admin.getEmail()).password(TestSupport.SENHA))
                .andExpect(redirectedUrl("/login?erro=inativo"));
        // usuários de outras empresas não são afetados
        mvc.perform(get("/app/dashboard").session(sOutra)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 3. hash da senha fora da sessão

    @Test
    @DisplayName("O hash da senha é apagado do usuário guardado na sessão (login e \"lembrar acesso\")")
    void hashDaSenhaNaoFicaNaSessao() throws Exception {
        Usuario u = ts.novaEmpresa("Sem Hash");
        MvcResult login = entrar(u, true);
        MockHttpSession sessao = sessao(login);

        UsuarioPrincipal p = principalDaSessao(sessao);
        assertThat(p.getPassword()).isNull();
        assertThat(p.getId()).isEqualTo(u.getId());
        assertThat(p.getEmpresaId()).isEqualTo(u.getEmpresa().getId());
        // a sessão continua válida sem o hash
        mvc.perform(get("/app/dashboard").session(sessao)).andExpect(status().isOk());

        // login automático pelo cookie "lembrar acesso" também não guarda o hash
        MvcResult auto = mvc.perform(get("/app/dashboard").cookie(login.getResponse().getCookie(LEMBRAR)))
                .andExpect(status().isOk()).andReturn();
        assertThat(principalDaSessao(sessao(auto)).getPassword()).isNull();
    }
}
