package br.com.vigentra;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.CategoriaRepository;
import br.com.vigentra.repository.ConfiguracaoRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("Cadastro de empresas e autenticação")
class AutenticacaoTest extends IntegracaoBase {

    @Autowired UsuarioRepository usuarioRepo;
    @Autowired CategoriaRepository categoriaRepo;
    @Autowired ConfiguracaoRepository configuracaoRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired LoginAttemptService attempts;

    @BeforeEach
    void reset() {
        attempts.limpar();
    }

    @Test
    @DisplayName("Cadastro de empresa cria administrador, categorias, configuração e senha com BCrypt")
    void cadastroDeEmpresa() throws Exception {
        String email = TestSupport.unico("novo") + "@empresa.com";
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Maria da Silva").param("empresa", "Empresa Nova Ltda")
                        .param("cnpj", "11.444.777/0001-61").param("email", email).param("telefone", "(11) 98888-7777")
                        .param("senha", "Forte@2026").param("confirmacaoSenha", "Forte@2026").param("aceiteTermos", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        Usuario u = usuarioRepo.findByEmailComEmpresa(email).orElseThrow();
        assertThat(u.getPerfil().getCodigo()).isEqualTo(PerfilCodigo.ADMIN);
        assertThat(u.getSenhaHash()).startsWith("$2a$").isNotEqualTo("Forte@2026");
        assertThat(encoder.matches("Forte@2026", u.getSenhaHash())).isTrue();
        assertThat(u.getTermosAceitosEm()).isNotNull();
        assertThat(u.getEmpresa().getCnpj()).isEqualTo("11.444.777/0001-61");
        assertThat(categoriaRepo.findTodas(u.getEmpresa().getId())).hasSize(8);
        assertThat(configuracaoRepo.findByEmpresaId(u.getEmpresa().getId())).isPresent();
    }

    @Test
    @DisplayName("Cadastro valida campos, termos, senha forte e e-mail duplicado")
    void validacoesDoCadastro() throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Jo").param("empresa", "").param("email", "invalido")
                        .param("senha", "fraca").param("confirmacaoSenha", "outra"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/cadastro"))
                .andExpect(model().attributeHasFieldErrors("form", "nome", "empresa", "email", "senha", "confirmacaoSenha", "aceiteTermos"));

        Usuario existente = ts.novaEmpresa("Duplicada");
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Outra Pessoa").param("empresa", "Outra").param("email", existente.getEmail())
                        .param("senha", "Forte@2026").param("confirmacaoSenha", "Forte@2026").param("aceiteTermos", "true"))
                .andExpect(view().name("auth/cadastro"))
                .andExpect(model().attributeHasErrors("form"));

        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Outra Pessoa").param("empresa", "Outra").param("cnpj", "11.111.111/1111-11")
                        .param("email", TestSupport.unico("x") + "@t.com")
                        .param("senha", "Forte@2026").param("confirmacaoSenha", "Forte@2026").param("aceiteTermos", "true"))
                .andExpect(view().name("auth/cadastro"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    @DisplayName("Login válido redireciona para o dashboard; inválido volta ao login")
    void login() throws Exception {
        Usuario u = ts.novaEmpresa("Login");
        mvc.perform(formLogin("/login").user(u.getEmail()).password(TestSupport.SENHA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/dashboard"));
        mvc.perform(formLogin("/login").user(u.getEmail()).password("errada"))
                .andExpect(redirectedUrl("/login?erro=credenciais"));
        assertThat(ts.recarregar(u).getUltimoLogin()).isNotNull();
    }

    @Test
    @DisplayName("Conta é bloqueada após 5 tentativas inválidas")
    void bloqueioPorTentativas() throws Exception {
        Usuario u = ts.novaEmpresa("Bloqueio");
        for (int i = 0; i < 4; i++) {
            mvc.perform(formLogin("/login").user(u.getEmail()).password("errada" + i))
                    .andExpect(redirectedUrl("/login?erro=credenciais"));
        }
        mvc.perform(formLogin("/login").user(u.getEmail()).password("errada-final"))
                .andExpect(redirectedUrl("/login?erro=bloqueado"));
        // mesmo com a senha correta, a conta continua bloqueada
        mvc.perform(formLogin("/login").user(u.getEmail()).password(TestSupport.SENHA))
                .andExpect(redirectedUrl("/login?erro=bloqueado"));
        assertThat(ts.recarregar(u).getBloqueadoAte()).isNotNull();
    }

    @Test
    @DisplayName("Usuário desativado não consegue entrar")
    void usuarioDesativado() throws Exception {
        Usuario admin = ts.novaEmpresa("Desativado");
        ts.logar(admin);
        Usuario outro = ts.usuario(PerfilCodigo.USUARIO);
        outro.setAtivo(false);
        usuarioRepo.save(outro);
        ts.deslogar();
        mvc.perform(formLogin("/login").user(outro.getEmail()).password(TestSupport.SENHA))
                .andExpect(redirectedUrl("/login?erro=inativo"));
    }

    @Test
    @DisplayName("Rotas protegidas exigem autenticação; POST sem token CSRF é recusado")
    void protecaoDeRotas() throws Exception {
        mvc.perform(get("/app/dashboard")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/app/api/calendario?ano=2026&mes=1")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/cadastro").param("nome", "x")).andExpect(status().isForbidden());
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/termos")).andExpect(status().isOk());
        mvc.perform(get("/privacidade")).andExpect(status().isOk());
        mvc.perform(get("/recuperar-senha")).andExpect(status().isOk());
        mvc.perform(get("/redefinir-senha?token=invalido")).andExpect(status().isOk())
                .andExpect(model().attribute("tokenValido", false));
    }
}
