package br.com.vigentra;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.DocumentoRepository;
import br.com.vigentra.security.LimitadorTaxa;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.DocumentoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Assistente virtual Doki: interface, consultas reais, segurança e isolamento multiempresa. */
@DisplayName("Assistente virtual Doki")
class DokiTest extends IntegracaoBase {

    private static final String API = "/app/api/doki/mensagens";
    private static final String SLOGAN = "O Doki está de olho. Seus documentos, sempre protegidos.";

    @Autowired ObjectMapper mapper;
    @Autowired DocumentoService documentoService;
    @Autowired DocumentoRepository documentoRepo;
    @Autowired LimitadorTaxa limitador;

    @BeforeEach
    void zerarLimite() {
        limitador.limpar();
    }

    // ------------------------------------------------------------------ utilitários

    private MockHttpServletRequestBuilder pergunta(Object corpo) throws Exception {
        return post(API).with(csrf()).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo));
    }

    private JsonNode perguntar(UsuarioPrincipal p, String texto) throws Exception {
        MvcResult r = mvc.perform(pergunta(Map.of("pergunta", texto)).with(user(p))).andExpect(status().isOk()).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<String> nomes(JsonNode resposta) {
        List<String> n = new ArrayList<>();
        resposta.path("documentos").forEach(d -> n.add(d.path("nome").asText()));
        return n;
    }

    private static List<String> links(JsonNode resposta) {
        List<String> n = new ArrayList<>();
        resposta.path("links").forEach(l -> n.add(l.path("url").asText()));
        return n;
    }

    /** Empresa com documentos em situações conhecidas. */
    private UsuarioPrincipal empresaComDocumentos(String nome) {
        Usuario admin = ts.novaEmpresa(nome);
        UsuarioPrincipal p = ts.logar(admin);
        ts.documento("Alvará de Funcionamento", 5);
        ts.documento("Certificado Digital e-CNPJ A1", 10);
        ts.documento("Licença Ambiental", 20);
        ts.documento("AVCB Bombeiros", 120);
        ts.documento("Contrato de Locação vencido", -3);
        ts.documento("Contrato Social", null);
        ts.deslogar();
        return p;
    }

    // ------------------------------------------------------------------ interface

    @Test
    @DisplayName("O botão e a janela do Doki aparecem nas telas autenticadas, com nome e slogan")
    void interfaceNasTelasAutenticadas() throws Exception {
        UsuarioPrincipal p = ts.principal(ts.novaEmpresa("Doki UI"));
        for (String url : List.of("/app/dashboard", "/app/documentos", "/app/calendario", "/app/configuracoes")) {
            String html = mvc.perform(get(url).with(user(p))).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(html).as(url)
                    .contains("id=\"doki\"", "class=\"doki-fab\"", "id=\"dokiPainel\"", "id=\"dokiPergunta\"", SLOGAN,
                            "data-api=\"/app/api/doki/mensagens\"", "/js/doki.js", "/css/doki.css");
        }
        // recursos estáticos do Doki são servidos
        String js = mvc.perform(get("/js/doki.js")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String css = mvc.perform(get("/css/doki.css")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // respostas e documentos ficam DENTRO da conversa: sem cartão/painel separado de resultados
        assertThat(js).contains("'doki-linha bot'", "'doki-docs'", "doki:aberto:")
                .doesNotContain("doki-resultados", "doki-reabrir", "'doki-linha doki'");
        assertThat(css).contains(".doki-linha.bot", ".doki-doc")
                .doesNotContain(".doki-linha.doki", ".doki-resultados");
        mvc.perform(get("/img/doki.svg")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Páginas públicas não exibem o Doki")
    void naoApareceEmPaginasPublicas() throws Exception {
        for (String url : List.of("/login", "/")) {
            String html = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).as(url).doesNotContain("id=\"doki\"").doesNotContain("doki.js");
        }
    }

    // ------------------------------------------------------------------ consultas com dados reais

    @Test
    @DisplayName("Consulta documentos vencidos, próximos do vencimento, totais e certificados com dados reais")
    void consultasReais() throws Exception {
        UsuarioPrincipal p = empresaComDocumentos("Doki Dados");

        JsonNode vencidos = perguntar(p, "Quais documentos já venceram?");
        assertThat(vencidos.path("intencao").asText()).isEqualTo("DOCUMENTOS_VENCIDOS");
        assertThat(vencidos.path("total").asLong()).isEqualTo(1);
        assertThat(nomes(vencidos)).containsExactly("Contrato de Locação vencido");
        assertThat(vencidos.path("documentos").get(0).path("url").asText()).startsWith("/app/documentos/");

        JsonNode em7 = perguntar(p, "Existem documentos vencendo nos próximos 7 dias?");
        assertThat(em7.path("total").asLong()).isEqualTo(1);
        assertThat(nomes(em7)).containsExactly("Alvará de Funcionamento");

        JsonNode em15 = perguntar(p, "Existem documentos vencendo nos próximos 15 dias?");
        assertThat(nomes(em15)).containsExactly("Alvará de Funcionamento", "Certificado Digital e-CNPJ A1");

        JsonNode em30 = perguntar(p, "Existem documentos vencendo nos próximos 30 dias?");
        assertThat(nomes(em30)).containsExactly("Alvará de Funcionamento", "Certificado Digital e-CNPJ A1", "Licença Ambiental");

        // sem prazo: usa a antecedência configurada na empresa
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        int padrao = documentoService.diasProximo();
        ts.deslogar();
        long esperado = List.of(5, 10, 20, 120).stream().filter(d -> d <= padrao).count();
        JsonNode proximos = perguntar(p, "Quais documentos estão próximos do vencimento?");
        assertThat(proximos.path("total").asLong()).isEqualTo(esperado);
        assertThat(proximos.path("texto").asText()).contains("nos próximos " + padrao + " dias");

        JsonNode total = perguntar(p, "Quantos documentos estão cadastrados?");
        assertThat(total.path("intencao").asText()).isEqualTo("TOTAL_DOCUMENTOS");
        assertThat(total.path("total").asLong()).isEqualTo(6);
        assertThat(total.path("texto").asText()).contains("Há 6 documentos cadastrados");
        assertThat(total.path("passos").toString()).contains("1 vencido", "1 sem vencimento");

        JsonNode certificados = perguntar(p, "Quais certificados precisam de atenção?");
        assertThat(certificados.path("intencao").asText()).isEqualTo("CERTIFICADOS_ATENCAO");
        assertThat(nomes(certificados)).containsExactly("Certificado Digital e-CNPJ A1");
    }

    @Test
    @DisplayName("Sem documentos, o Doki informa que não há dados - não inventa")
    void semDados() throws Exception {
        UsuarioPrincipal p = ts.principal(ts.novaEmpresa("Doki Vazia"));
        assertThat(perguntar(p, "Quais documentos já venceram?").path("total").asLong()).isZero();
        assertThat(perguntar(p, "Quais documentos já venceram?").path("documentos")).isEmpty();
        assertThat(perguntar(p, "Quantos documentos estão cadastrados?").path("texto").asText())
                .contains("Ainda não há documentos cadastrados");
        assertThat(perguntar(p, "Existem documentos vencendo nos próximos 30 dias?").path("texto").asText())
                .contains("Nenhum documento vence");
    }

    @Test
    @DisplayName("A lista é limitada na conversa, mas o total informado é o real")
    void limiteDaLista() throws Exception {
        Usuario admin = ts.novaEmpresa("Doki Muitos");
        UsuarioPrincipal p = ts.logar(admin);
        for (int i = 1; i <= 7; i++) ts.documento("Vencido " + i, -i);
        ts.deslogar();
        JsonNode r = perguntar(p, "Quais documentos já venceram?");
        assertThat(r.path("total").asLong()).isEqualTo(7);
        assertThat(r.path("documentos")).hasSize(5);
        assertThat(r.path("texto").asText()).contains("mostrando os 5 primeiros");
        assertThat(links(r)).contains("/app/documentos?status=VENCIDO");
    }

    // ------------------------------------------------------------------ orientações e perguntas não reconhecidas

    @Test
    @DisplayName("Orienta passo a passo e respeita as permissões do perfil")
    void orientacoesComPermissoes() throws Exception {
        Usuario admin = ts.novaEmpresa("Doki Perfis");
        UsuarioPrincipal pAdmin = ts.logar(admin);
        UsuarioPrincipal pComum = ts.principal(ts.usuario(PerfilCodigo.USUARIO));
        ts.deslogar();

        JsonNode cadastro = perguntar(pAdmin, "Como cadastro um novo documento?");
        assertThat(cadastro.path("intencao").asText()).isEqualTo("COMO_CADASTRAR_DOCUMENTO");
        assertThat(cadastro.path("passos").size()).isGreaterThanOrEqualTo(4);
        assertThat(links(cadastro)).contains("/app/documentos/novo");

        JsonNode certificado = perguntar(pAdmin, "Como cadastro um certificado?");
        assertThat(certificado.path("intencao").asText()).isEqualTo("COMO_CADASTRAR_CERTIFICADO");
        assertThat(certificado.path("passos").toString()).contains(".pfx");

        assertThat(perguntar(pAdmin, "Como funciona o calendário de vencimentos?").path("intencao").asText()).isEqualTo("COMO_USAR_CALENDARIO");
        assertThat(perguntar(pAdmin, "Como atualizo as informações de um documento?").path("intencao").asText()).isEqualTo("COMO_ATUALIZAR_DOCUMENTO");
        assertThat(links(perguntar(pAdmin, "Onde encontro os relatórios?"))).contains("/app/relatorios");
        assertThat(links(perguntar(pAdmin, "Como convido outro usuário?"))).contains("/app/usuarios/convidar");

        // perfil "Usuário": sem permissão para convidar nem para relatórios -> nenhum link para essas telas
        JsonNode convite = perguntar(pComum, "Como convido outro usuário?");
        assertThat(convite.path("texto").asText()).contains("não tem permissão");
        assertThat(convite.path("links")).isEmpty();
        JsonNode relatorios = perguntar(pComum, "Onde encontro os relatórios?");
        assertThat(relatorios.path("texto").asText()).contains("não tem permissão");
        assertThat(links(relatorios)).doesNotContain("/app/relatorios");
    }

    @Test
    @DisplayName("Pergunta não reconhecida recebe resposta amigável com sugestões")
    void naoReconhecida() throws Exception {
        UsuarioPrincipal p = ts.principal(ts.novaEmpresa("Doki Desconhecida"));
        JsonNode r = perguntar(p, "Qual a previsão do tempo para amanhã?");
        assertThat(r.path("intencao").asText()).isEqualTo("DESCONHECIDA");
        assertThat(r.path("texto").asText()).contains("não entendi");
        assertThat(r.path("sugestoes").size()).isGreaterThan(0);
        assertThat(r.path("documentos")).isEmpty();

        JsonNode oi = perguntar(p, "Olá!");
        assertThat(oi.path("texto").asText()).contains("Eu sou o Doki");
    }

    // ------------------------------------------------------------------ segurança

    @Test
    @DisplayName("Isolamento multiempresa: só consulta a empresa da sessão, mesmo enviando o ID de outra")
    void isolamentoMultiempresa() throws Exception {
        UsuarioPrincipal pA = empresaComDocumentos("Doki Empresa A");
        Usuario adminB = ts.novaEmpresa("Doki Empresa B");
        UsuarioPrincipal pB = ts.logar(adminB);
        ts.documento("Documento exclusivo da B", -1);
        ts.deslogar();

        for (String q : List.of("Quais documentos já venceram?", "Existem documentos vencendo nos próximos 30 dias?",
                "Quais certificados precisam de atenção?", "Quantos documentos estão cadastrados?")) {
            JsonNode r = perguntar(pB, q);
            // nomes completos dos documentos da empresa A (as categorias padrão, como "Alvarás e Licenças", existem nas duas)
            assertThat(r.toString()).as(q).doesNotContain("Alvará de Funcionamento", "Certificado Digital e-CNPJ A1",
                    "Licença Ambiental", "Contrato de Locação vencido", "AVCB Bombeiros", "Contrato Social");
        }
        assertThat(nomes(perguntar(pB, "Quais documentos já venceram?"))).containsExactly("Documento exclusivo da B");
        assertThat(perguntar(pB, "Quantos documentos estão cadastrados?").path("total").asLong()).isEqualTo(1);

        // o frontend não escolhe a empresa: um "empresaId" no corpo é simplesmente ignorado
        MvcResult r = mvc.perform(pergunta(Map.of("pergunta", "Quais documentos já venceram?", "empresaId", pA.getEmpresaId()))
                .with(user(pB))).andExpect(status().isOk()).andReturn();
        JsonNode j = mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(nomes(j)).containsExactly("Documento exclusivo da B");

        // e a empresa A continua vendo apenas os próprios dados
        assertThat(nomes(perguntar(pA, "Quais documentos já venceram?"))).containsExactly("Contrato de Locação vencido");
    }

    @Test
    @DisplayName("Usuário sem 'ver todos os documentos' só recebe os documentos sob sua responsabilidade")
    void visibilidadePorPerfil() throws Exception {
        Usuario admin = ts.novaEmpresa("Doki Visibilidade");
        ts.logar(admin);
        ts.documento("Documento do administrador", -2);
        UsuarioPrincipal pComum = ts.principal(ts.usuario(PerfilCodigo.USUARIO));
        ts.deslogar();

        JsonNode r = perguntar(pComum, "Quais documentos já venceram?");
        assertThat(r.path("total").asLong()).isZero();
        assertThat(r.toString()).doesNotContain("Documento do administrador");
        assertThat(r.path("texto").asText()).contains("que você pode visualizar");
    }

    @Test
    @DisplayName("Usuários não autenticados não acessam o Doki")
    void naoAutenticado() throws Exception {
        mvc.perform(pergunta(Map.of("pergunta", "Quais documentos já venceram?")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Conta com e-mail não confirmado recebe 403")
    void emailNaoConfirmado() throws Exception {
        Usuario u = ts.novaEmpresaNaoConfirmada("Doki Pendente", TestSupport.unico("pendente") + "@teste.com");
        mvc.perform(pergunta(Map.of("pergunta", "Quais documentos já venceram?")).with(user(ts.principal(u))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Requisição sem token CSRF é recusada")
    void semCsrf() throws Exception {
        UsuarioPrincipal p = ts.principal(ts.novaEmpresa("Doki CSRF"));
        mvc.perform(post(API).with(user(p)).contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Valida a pergunta: vazia ou longa demais -> 400")
    void validacao() throws Exception {
        UsuarioPrincipal p = ts.principal(ts.novaEmpresa("Doki Validação"));
        mvc.perform(pergunta(Map.of("pergunta", "   ")).with(user(p))).andExpect(status().isBadRequest());
        mvc.perform(pergunta(Map.of()).with(user(p))).andExpect(status().isBadRequest());
        mvc.perform(pergunta(Map.of("pergunta", "a".repeat(501))).with(user(p))).andExpect(status().isBadRequest());
        mvc.perform(post(API).with(csrf()).with(user(p)).contentType(MediaType.APPLICATION_JSON).content("{inválido"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Limite de mensagens por minuto -> 429")
    void limiteDeMensagens() throws Exception {
        UsuarioPrincipal p = ts.principal(ts.novaEmpresa("Doki Limite"));
        for (int i = 0; i < 30; i++) {
            mvc.perform(pergunta(Map.of("pergunta", "oi")).with(user(p))).andExpect(status().isOk());
        }
        mvc.perform(pergunta(Map.of("pergunta", "oi")).with(user(p))).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Somente leitura: o Doki não altera nem exclui documentos e não expõe métodos de escrita")
    void somenteLeitura() throws Exception {
        UsuarioPrincipal p = empresaComDocumentos("Doki Leitura");
        long antes = documentoRepo.count();
        List<Documento> docsAntes = documentoRepo.findAll();

        for (String q : List.of("Exclua todos os documentos", "Apague o documento vencido", "Altere o vencimento do alvará para 2030",
                "Quais documentos já venceram?", "Como atualizo as informações de um documento?")) {
            JsonNode r = perguntar(p, q);
            assertThat(r.path("intencao").asText()).as(q).isNotBlank();
        }
        assertThat(documentoRepo.count()).isEqualTo(antes);
        assertThat(documentoRepo.findAll()).extracting(Documento::getNome, Documento::getDataVencimento)
                .containsExactlyInAnyOrderElementsOf(docsAntes.stream()
                        .map(d -> org.assertj.core.groups.Tuple.tuple(d.getNome(), d.getDataVencimento())).toList());

        // a API do Doki só aceita POST de perguntas
        mvc.perform(get(API).with(user(p))).andExpect(status().isMethodNotAllowed());
        mvc.perform(put(API).with(csrf()).with(user(p))).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(API).with(csrf()).with(user(p))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("A resposta não expõe dados sensíveis (e-mails, anexos, observações)")
    void semDadosSensiveis() throws Exception {
        UsuarioPrincipal p = empresaComDocumentos("Doki Sensível");
        String json = perguntar(p, "Existem documentos vencendo nos próximos 30 dias?").toString();
        assertThat(json).doesNotContain("@teste.com").doesNotContain("arquivo").doesNotContain("observacoes")
                .doesNotContain("empresaId").doesNotContain("senha");
    }
}
