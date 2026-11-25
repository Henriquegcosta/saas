package br.com.vigentra;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.alerta.AlertaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Teste de fumaça da interface: renderiza TODAS as páginas (com dados) para garantir
 * que não há erro de template, link quebrado no menu ou falha de navegação.
 */
@DisplayName("Renderização de todas as páginas")
class PaginasTest extends IntegracaoBase {

    @Autowired AlertaService alertaService;
    @Autowired DocumentoService documentoService;

    private void deveAbrir(UsuarioPrincipal p, String url) throws Exception {
        MvcResult r = mvc.perform(get(url).with(user(p))).andReturn();
        assertThat(r.getResponse().getStatus()).as("GET " + url + " -> " + r.getResolvedException()).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).as(url).doesNotContain("Whitelabel").doesNotContain("TemplateProcessingException");
    }

    @Test
    @DisplayName("Administrador abre todas as páginas da área logada")
    void paginasDoAdministrador() throws Exception {
        Usuario admin = ts.novaEmpresa("Páginas");
        var p = ts.logar(admin);
        Documento d = ts.documento("Alvará de Funcionamento", 8);
        ts.documento("Contrato vencido", -2);
        ts.documento("Contrato social", null);
        Usuario comum = ts.usuario(PerfilCodigo.USUARIO);
        alertaService.executarEmpresa(admin.getEmpresa().getId(), documentoService.hoje());
        var hoje = documentoService.hoje();

        List<String> urls = List.of(
                "/app/dashboard",
                "/app/documentos", "/app/documentos?status=VENCIDO", "/app/documentos?q=alvara&ordem=nome&pagina=0",
                "/app/documentos?vencimentoDe=" + hoje + "&vencimentoAte=" + hoje.plusDays(30),
                "/app/documentos/novo", "/app/documentos/" + d.getId(), "/app/documentos/" + d.getId() + "/editar",
                "/app/calendario", "/app/api/calendario?ano=" + hoje.getYear() + "&mes=" + hoje.getMonthValue(),
                "/app/notificacoes", "/app/notificacoes?lida=false", "/app/notificacoes/envios", "/app/notificacoes/envios?status=MODO_DEV",
                "/app/atividades", "/app/atividades?acao=DOCUMENTO_CRIADO",
                "/app/relatorios", "/app/relatorios?status=VENCIDO",
                "/app/usuarios", "/app/usuarios/novo", "/app/usuarios/convidar", "/app/usuarios/" + comum.getId() + "/editar",
                "/app/responsaveis", "/app/responsaveis/novo", "/app/responsaveis/" + d.getResponsavel().getId() + "/editar",
                "/app/notificacoes/envios?canal=EMAIL&tipoMensagem=ALERTA_VENCIMENTO",
                "/app/configuracoes?aba=canais",
                "/app/configuracoes", "/app/configuracoes?aba=senha", "/app/configuracoes?aba=notificacoes",
                "/app/configuracoes?aba=empresa", "/app/configuracoes?aba=alertas", "/app/configuracoes?aba=email",
                "/app/configuracoes?aba=sistema", "/app/configuracoes?aba=categorias", "/app/configuracoes?aba=permissoes",
                "/app/configuracoes?aba=categorias&editarCategoria=" + d.getCategoria().getId(),
                "/app/configuracoes/meus-dados");
        for (String url : urls) deveAbrir(p, url);
    }

    @Test
    @DisplayName("Usuário comum abre as páginas permitidas")
    void paginasDoUsuario() throws Exception {
        Usuario admin = ts.novaEmpresa("Páginas usuário");
        ts.logar(admin);
        Usuario comum = ts.usuario(PerfilCodigo.USUARIO);
        var p = ts.logar(comum);
        for (String url : List.of("/app/dashboard", "/app/documentos", "/app/documentos/novo", "/app/calendario",
                "/app/notificacoes", "/app/configuracoes", "/app/configuracoes?aba=senha")) {
            deveAbrir(p, url);
        }
    }

    @Test
    @DisplayName("Páginas públicas e de erro")
    void paginasPublicas() throws Exception {
        for (String url : List.of("/", "/login", "/cadastro", "/recuperar-senha", "/termos", "/privacidade",
                "/confirmar-email?token=invalido-mas-bem-formado-123", "/confirmar-email/alteracao?token=x",
                "/convite?token=invalido-mas-bem-formado-123")) {
            MvcResult r = mvc.perform(get(url)).andReturn();
            assertThat(r.getResponse().getStatus()).as(url).isEqualTo(200);
        }
        assertThat(mvc.perform(get("/nao-existe")).andReturn().getResponse().getStatus()).isIn(302, 404);
    }
}
