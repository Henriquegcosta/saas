package br.com.vigentra;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.security.UsuarioPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mensagem de "lista vazia" da tela de usuários: só aparece quando o resultado é realmente vazio.
 * (Antes, th:if e th:replace no mesmo elemento faziam a mensagem aparecer sempre.)
 */
@DisplayName("Tela de usuários: pesquisa e mensagem de lista vazia")
class UsuarioListaTest extends IntegracaoBase {

    private static final String NAO_ENCONTRADO = "Nenhum usuário encontrado";
    private static final String NAO_CADASTRADO = "Nenhum usuário cadastrado";
    private static final String TABELA = "<table class=\"table table-hover\">";

    private UsuarioPrincipal admin;
    private Usuario comum;

    @BeforeEach
    void preparar() {
        Usuario u = ts.novaEmpresa("Lista de Usuarios");
        admin = ts.logar(u);
        comum = ts.usuario(PerfilCodigo.USUARIO);
    }

    private String abrir(String url) throws Exception {
        return mvc.perform(get(url).with(user(admin))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Sem pesquisa e sem filtros: lista os usuários e não mostra mensagem de vazio")
    void semFiltros() throws Exception {
        String html = abrir("/app/usuarios");
        assertThat(html).contains(TABELA, "Admin Lista de Usuarios", comum.getEmail())
                .doesNotContain(NAO_ENCONTRADO).doesNotContain(NAO_CADASTRADO);
    }

    @Test
    @DisplayName("Pesquisa com resultado: mostra o usuário encontrado e esconde a mensagem")
    void pesquisaComResultado() throws Exception {
        String html = abrir("/app/usuarios?q=" + comum.getEmail());
        assertThat(html).contains(TABELA, comum.getEmail())
                .doesNotContain(NAO_ENCONTRADO).doesNotContain(NAO_CADASTRADO);
    }

    @Test
    @DisplayName("Filtros com resultado (perfil e situação): mostra a lista e esconde a mensagem")
    void filtrosComResultado() throws Exception {
        String html = abrir("/app/usuarios?perfil=USUARIO&ativo=true");
        assertThat(html).contains(TABELA, comum.getEmail()).doesNotContain(NAO_ENCONTRADO);
    }

    @Test
    @DisplayName("Pesquisa sem resultado: mostra a mensagem e não mostra a tabela")
    void pesquisaSemResultado() throws Exception {
        String html = abrir("/app/usuarios?q=ninguem-com-este-nome");
        assertThat(html).contains(NAO_ENCONTRADO).doesNotContain(TABELA).doesNotContain(NAO_CADASTRADO);
    }

    @Test
    @DisplayName("Filtro sem resultado: mostra a mensagem e não mostra a tabela")
    void filtroSemResultado() throws Exception {
        String html = abrir("/app/usuarios?perfil=GESTOR");
        assertThat(html).contains(NAO_ENCONTRADO).doesNotContain(TABELA);
        assertThat(abrir("/app/usuarios?ativo=false")).contains(NAO_ENCONTRADO).doesNotContain(TABELA);
    }

    @Test
    @DisplayName("Pesquisa só com espaços conta como sem pesquisa")
    void pesquisaEmBranco() throws Exception {
        // .param(): na URL, o MockMvc codificaria o "%" de "%20" de novo
        String html = mvc.perform(get("/app/usuarios").param("q", "   ").with(user(admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains(TABELA).doesNotContain(NAO_ENCONTRADO);
    }
}
