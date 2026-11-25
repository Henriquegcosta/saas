package br.com.vigentra;

import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.repository.LogAtividadeRepository;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.alerta.AlertaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Estados vazios de todas as listagens:
 *  A - tela sem filtros e sem registros: mensagem de "nada cadastrado" (sem pedir para ajustar filtros);
 *  B - com registros (com ou sem filtros): listagem, sem mensagem de vazio;
 *  C - pesquisa/filtro sem resultado: mensagem de "nada encontrado" + "Limpar filtros", sem tabela vazia.
 * Os dados ficam no H2 dos testes; nenhum banco real é usado.
 */
@DisplayName("Estados vazios, pesquisas sem resultado e listagens")
class EstadosVaziosTest extends IntegracaoBase {

    /** botão do estado vazio de pesquisa (a barra de filtros de algumas telas tem outro "Limpar filtros") */
    private static final String LIMPAR = "<i class=\"bi bi-x-circle me-1\"></i>Limpar filtros";

    @Autowired AlertaService alertaService;
    @Autowired DocumentoService documentoService;
    @Autowired ResponsavelRepository responsavelRepo;
    @Autowired LogAtividadeRepository logRepo;
    @Autowired HistoricoEnvioRepository historicoRepo;

    private String html(UsuarioPrincipal p, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(user(p))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String html(UsuarioPrincipal p, String url) throws Exception {
        return html(p, get(url));
    }

    /** O cadastro da empresa registra o e-mail de confirmação no histórico de envios: removido só neste H2 de teste. */
    private void semHistoricoDeEnvios(Usuario admin) {
        Long empresaId = admin.getEmpresa().getId();
        historicoRepo.deleteAll(historicoRepo.findAll().stream().filter(h -> empresaId.equals(h.getEmpresa().getId())).toList());
    }

    // ------------------------------------------------------------------ Responsáveis
    @Nested @DisplayName("Responsáveis")
    class Responsaveis {
        private static final String CADASTRADO = "Nenhum responsável cadastrado";
        private static final String ENCONTRADO = "Nenhum responsável encontrado";

        @Test @DisplayName("A: sem registros mostra 'cadastrado' com botão de cadastro, sem falar em filtros")
        void semRegistros() throws Exception {
            Usuario admin = ts.novaEmpresa("Resp Vazio");
            var p = ts.logar(admin);
            // a empresa nasce com o administrador como responsável: removido só neste H2 de teste
            responsavelRepo.deleteAll(responsavelRepo.findTodos(admin.getEmpresa().getId()));
            assertThat(html(p, "/app/responsaveis")).contains(CADASTRADO, "Cadastrar responsável")
                    .doesNotContain(ENCONTRADO).doesNotContain(LIMPAR).doesNotContain("<table");
        }

        @Test @DisplayName("B: com registros mostra a lista e nenhuma mensagem")
        void comRegistros() throws Exception {
            Usuario admin = ts.novaEmpresa("Resp Lista");
            var p = ts.logar(admin);
            String h = html(p, "/app/responsaveis");
            assertThat(h).contains("<table", admin.getEmail()).doesNotContain(CADASTRADO).doesNotContain(ENCONTRADO);
            assertThat(html(p, get("/app/responsaveis").param("q", admin.getEmail())))
                    .contains("<table", admin.getEmail()).doesNotContain(ENCONTRADO);
        }

        @Test @DisplayName("C: pesquisa sem resultado mostra 'encontrado' e Limpar filtros, sem tabela")
        void pesquisaSemResultado() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Resp Busca"));
            assertThat(html(p, get("/app/responsaveis").param("q", "ninguem-assim")))
                    .contains(ENCONTRADO, LIMPAR).doesNotContain(CADASTRADO).doesNotContain("<table");
        }
    }

    // ------------------------------------------------------------------ Atividades (logs)
    @Nested @DisplayName("Atividades")
    class Atividades {
        private static final String REGISTRADA = "Nenhuma atividade registrada";
        private static final String ENCONTRADA = "Nenhuma atividade encontrada";

        @Test @DisplayName("A: sem registros mostra 'registrada', sem pedir para ajustar filtros")
        void semRegistros() throws Exception {
            Usuario admin = ts.novaEmpresa("Log Vazio");
            var p = ts.logar(admin);
            Long empresaId = admin.getEmpresa().getId();
            logRepo.deleteAll(logRepo.findAll().stream()
                    .filter(l -> l.getEmpresa() != null && empresaId.equals(l.getEmpresa().getId())).toList());
            assertThat(html(p, "/app/atividades")).contains(REGISTRADA)
                    .doesNotContain(ENCONTRADA).doesNotContain("Ajuste os filtros").doesNotContain("<table class=\"table\">");
        }

        @Test @DisplayName("B: com registros mostra a tabela e nenhuma mensagem")
        void comRegistros() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Log Lista"));
            ts.documento("Documento para o log", 30);
            assertThat(html(p, "/app/atividades")).contains("<table class=\"table\">")
                    .doesNotContain(REGISTRADA).doesNotContain(ENCONTRADA);
        }

        @Test @DisplayName("C: filtros sem resultado mostram 'encontrada' e Limpar filtros, sem tabela")
        void filtroSemResultado() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Log Busca"));
            for (var req : List.of(get("/app/atividades").param("q", "zzz-nada"),
                                   get("/app/atividades").param("de", "2001-01-01").param("ate", "2001-01-02"))) {
                assertThat(html(p, req)).contains(ENCONTRADA, LIMPAR)
                        .doesNotContain(REGISTRADA).doesNotContain("<table class=\"table\">");
            }
        }
    }

    // ------------------------------------------------------------------ Documentos
    @Nested @DisplayName("Documentos")
    class Documentos {
        private static final String CADASTRADO = "Nenhum documento cadastrado";
        private static final String ENCONTRADO = "Nenhum documento encontrado";

        @Test @DisplayName("A: sem documentos mostra só 'cadastrado' (antes apareciam as duas mensagens)")
        void semRegistros() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Doc Vazio"));
            assertThat(html(p, "/app/documentos")).contains(CADASTRADO, "Cadastrar primeiro documento")
                    .doesNotContain(ENCONTRADO).doesNotContain(LIMPAR + "</a>\n    </div>");
        }

        @Test @DisplayName("B: com documentos (com e sem pesquisa) mostra a lista e nenhuma mensagem")
        void comRegistros() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Doc Lista"));
            ts.documento("Alvará de Funcionamento", 20);
            for (String url : List.of("/app/documentos", "/app/documentos?q=Funcionamento")) {
                assertThat(html(p, url)).as(url).contains("class=\"doc-name\"", "Alvará de Funcionamento")
                        .doesNotContain(CADASTRADO).doesNotContain(ENCONTRADO);
            }
        }

        @Test @DisplayName("C: pesquisa sem resultado mostra só 'encontrado' com Limpar filtros")
        void pesquisaSemResultado() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Doc Busca"));
            ts.documento("Contrato social", 20);
            // o nome também aparece no filtro de responsáveis ("Resp ..."): verifica-se a linha da tabela
            assertThat(html(p, "/app/documentos?q=nada-parecido")).contains(ENCONTRADO, LIMPAR)
                    .doesNotContain(CADASTRADO).doesNotContain("class=\"doc-name\"");
        }

        @Test @DisplayName("Detalhe: documento sem anexo e sem alertas mostra os estados vazios do detalhe")
        void detalheSemAnexo() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Doc Detalhe"));
            Documento d = ts.documento("Licença sanitária", 40);
            assertThat(html(p, "/app/documentos/" + d.getId())).contains("Nenhum arquivo anexado", "Nenhum alerta enviado");
        }
    }

    // ------------------------------------------------------------------ Notificações
    @Nested @DisplayName("Notificações")
    class Notificacoes {

        @Test @DisplayName("Minhas - A: sem notificações mostra 'Nenhuma notificação', sem Limpar filtros")
        void minhasSemRegistros() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Notif Vazio"));
            assertThat(html(p, "/app/notificacoes")).contains("Nenhuma notificação")
                    .doesNotContain("Nenhuma notificação encontrada").doesNotContain(LIMPAR);
        }

        @Test @DisplayName("Minhas - B e C: com notificações mostra a lista; filtro sem resultado mostra 'encontrada'")
        void minhasComRegistrosEFiltro() throws Exception {
            Usuario admin = ts.novaEmpresa("Notif Lista");
            var p = ts.logar(admin);
            ts.documento("Certidão negativa", 3);
            alertaService.executarEmpresa(admin.getEmpresa().getId(), documentoService.hoje());
            String todas = html(p, "/app/notificacoes");
            assertThat(todas).contains("Certidão negativa").doesNotContain("Nenhuma notificação");
            // são todas não lidas: o filtro "Lidas" fica sem resultado
            // o nome continua no menu de notificações do cabeçalho; verifica-se a lista principal
            assertThat(html(p, "/app/notificacoes?lida=true")).contains("Nenhuma notificação encontrada", LIMPAR)
                    .doesNotContain("align-items-start px-3 py-3 border-bottom");
        }

        @Test @DisplayName("Envios - A, B e C")
        void envios() throws Exception {
            Usuario admin = ts.novaEmpresa("Envios");
            var p = ts.logar(admin);
            semHistoricoDeEnvios(admin);
            assertThat(html(p, "/app/notificacoes/envios")).contains("Nenhum envio registrado")
                    .doesNotContain("Nenhum envio encontrado").doesNotContain(LIMPAR);

            ts.logar(admin);   // a requisição do MockMvc limpa o contexto de segurança do ajudante de teste
            ts.documento("Alvará de bombeiros", 3);
            alertaService.executarEmpresa(admin.getEmpresa().getId(), documentoService.hoje());
            assertThat(html(p, "/app/notificacoes/envios")).contains("Alvará de bombeiros")
                    .doesNotContain("Nenhum envio registrado").doesNotContain("Nenhum envio encontrado");
            assertThat(html(p, get("/app/notificacoes/envios").param("q", "zzz-nada")))
                    .contains("Nenhum envio encontrado", LIMPAR).doesNotContain("Nenhum envio registrado");
        }
    }

    // ------------------------------------------------------------------ Dashboard
    @Nested @DisplayName("Dashboard")
    class Dashboard {

        @Test @DisplayName("Empresa sem documentos: estados vazios coerentes (sem 'Excelente!')")
        void semDocumentos() throws Exception {
            Usuario admin = ts.novaEmpresa("Painel Vazio");
            var p = ts.logar(admin);
            semHistoricoDeEnvios(admin);
            assertThat(html(p, "/app/dashboard")).contains("Cadastre o primeiro documento", "Nenhum vencimento próximo",
                            "Os documentos vencidos aparecerão aqui.", "Nenhum documento cadastrado", "Nenhum alerta enviado")
                    .doesNotContain("Excelente!");
        }

        @Test @DisplayName("Com vencimentos: a lista aparece e 'Nenhum vencimento próximo' some (antes aparecia sempre)")
        void comDocumentos() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Painel Lista"));
            ts.documento("Alvará de Funcionamento", 8);
            ts.documento("Contrato vencido", -2);
            assertThat(html(p, "/app/dashboard")).contains("Alvará de Funcionamento", "Contrato vencido")
                    .doesNotContain("Nenhum vencimento próximo").doesNotContain("Nenhum documento vencido")
                    .doesNotContain("Cadastre o primeiro documento");
        }

        @Test @DisplayName("Sem vencidos, mas com documentos: mensagem positiva")
        void semVencidos() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Painel Em Dia"));
            ts.documento("Alvará em dia", 200);
            assertThat(html(p, "/app/dashboard")).contains("Nenhum documento vencido", "Excelente!");
        }
    }

    // ------------------------------------------------------------------ Relatórios
    @Nested @DisplayName("Relatórios")
    class Relatorios {

        @Test @DisplayName("Sem documentos e sem filtros: 'Nenhum documento cadastrado', sem falar em filtros")
        void semDocumentos() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Rel Vazio"));
            assertThat(html(p, "/app/relatorios")).contains("<h3>Nenhum documento cadastrado</h3>")
                    .doesNotContain("<h3>Nenhum documento para os filtros</h3>").doesNotContain("Sem dados para os filtros");
        }

        @Test @DisplayName("Filtro sem resultado: 'Nenhum documento para os filtros'; com dados: sem mensagem")
        void filtroSemResultado() throws Exception {
            var p = ts.logar(ts.novaEmpresa("Rel Filtro"));
            ts.documento("Documento em dia", 200);
            assertThat(html(p, "/app/relatorios")).doesNotContain("<h3>Nenhum documento para os filtros</h3>")
                    .doesNotContain("<h3>Nenhum documento cadastrado</h3>");
            assertThat(html(p, "/app/relatorios?status=VENCIDO")).contains("<h3>Nenhum documento para os filtros</h3>")
                    .doesNotContain("<h3>Nenhum documento cadastrado</h3>");
        }
    }

    // ------------------------------------------------------------------ Proteção contra regressão
    @Test
    @DisplayName("Nenhum template combina th:replace/th:insert com th:if, th:unless, th:each ou th:with no mesmo elemento")
    void semCondicaoIgnoradaPeloReplace() throws IOException {
        Pattern tag = Pattern.compile("<(?!/|!)[a-zA-Z:]+\\b([^<>]*?)/?>", Pattern.DOTALL);
        Pattern inclusao = Pattern.compile("\\bth:(replace|insert)=");
        Pattern condicao = Pattern.compile("\\bth:(if|unless|each|with)=");
        List<String> problemas = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path f : arquivos.filter(a -> a.toString().endsWith(".html")).toList()) {
                String txt = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = tag.matcher(txt);
                while (m.find()) {
                    if (inclusao.matcher(m.group(1)).find() && condicao.matcher(m.group(1)).find()) {
                        long linha = txt.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                        problemas.add(f + ":" + linha);
                    }
                }
            }
        }
        assertThat(problemas).as("o th:replace é processado antes e a condição é ignorada; use um <th:block> externo")
                .isEmpty();
    }
}
