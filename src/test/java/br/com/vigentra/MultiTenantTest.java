package br.com.vigentra;

import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.repository.NotificacaoRepository;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.NotificacaoService;
import br.com.vigentra.service.RecursoNaoEncontradoException;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.service.alerta.AlertaService;
import br.com.vigentra.service.relatorio.RelatorioService;
import br.com.vigentra.web.dto.DocumentoForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento multi-tenant: um usuário da empresa B NUNCA acessa dados da empresa A,
 * mesmo conhecendo os IDs e chamando as URLs diretamente.
 */
@DisplayName("Isolamento multi-tenant")
class MultiTenantTest extends IntegracaoBase {

    @Autowired DocumentoService documentoService;
    @Autowired UsuarioService usuarioService;
    @Autowired RelatorioService relatorioService;
    @Autowired NotificacaoService notificacaoService;
    @Autowired NotificacaoRepository notificacaoRepo;
    @Autowired AlertaService alertaService;

    @Test
    @DisplayName("Documentos, usuários, relatórios e notificações ficam isolados por empresa")
    void isolamento() throws Exception {
        Usuario adminA = ts.novaEmpresa("Empresa A");
        ts.logar(adminA);
        Documento docA = ts.documento("Documento secreto da empresa A", 5);
        Long categoriaA = docA.getCategoria().getId();
        ts.deslogar();
        alertaService.executarEmpresa(adminA.getEmpresa().getId(), documentoService.hoje());

        Usuario adminB = ts.novaEmpresa("Empresa B");
        var pB = ts.logar(adminB);
        ts.documento("Documento da empresa B", 50);

        // serviço: listagem, busca por ID, relatórios e usuários
        assertThat(documentoService.listarTodos(new DocumentoFiltro())).extracting(Documento::getNome)
                .containsExactly("Documento da empresa B");
        assertThatThrownBy(() -> documentoService.buscar(docA.getId())).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(relatorioService.gerar(new DocumentoFiltro()).documentos()).hasSize(1);
        assertThat(usuarioService.todos()).extracting(Usuario::getId).doesNotContain(adminA.getId());
        assertThatThrownBy(() -> usuarioService.buscar(adminA.getId())).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(notificacaoService.historicoRecente(50))
                .noneMatch(h -> h.getDocumentoNome().contains("secreto"))
                .allMatch(h -> h.getEmpresa().getId().equals(adminB.getEmpresa().getId()));

        // tentar gravar um documento de B apontando para a categoria de A é recusado
        DocumentoForm f = new DocumentoForm();
        f.setNome("Tentativa");
        f.setCategoriaId(categoriaA);
        f.setSemVencimento(true);
        assertThatThrownBy(() -> documentoService.salvar(f, null)).hasMessageContaining("Categoria inválida");

        // tentar editar o documento de A informando o ID dele também é recusado
        DocumentoForm invasao = new DocumentoForm();
        invasao.setId(docA.getId());
        invasao.setNome("Alterado por B");
        invasao.setCategoriaId(ts.categoria().getId());
        invasao.setSemVencimento(true);
        assertThatThrownBy(() -> documentoService.salvar(invasao, null)).isInstanceOf(RecursoNaoEncontradoException.class);

        // HTTP: acesso direto por URL devolve 404 (não revela que o registro existe)
        mvc.perform(get("/app/documentos/" + docA.getId()).with(user(pB))).andExpect(status().isNotFound());
        mvc.perform(get("/app/documentos/" + docA.getId() + "/editar").with(user(pB))).andExpect(status().isNotFound());
        mvc.perform(get("/app/documentos/" + docA.getId() + "/arquivo").with(user(pB))).andExpect(status().isNotFound());
        mvc.perform(post("/app/documentos/" + docA.getId() + "/excluir").with(csrf()).with(user(pB))).andExpect(status().isNotFound());
        mvc.perform(get("/app/usuarios/" + adminA.getId() + "/editar").with(user(pB))).andExpect(status().isNotFound());
        mvc.perform(get("/app/documentos?q=secreto").with(user(pB)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Documento secreto da empresa A"))));
        mvc.perform(get("/app/relatorios/exportar/csv").with(user(pB)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));

        // notificação da empresa A não pode ser aberta por B
        var notifA = notificacaoRepo.findNaoLidas(adminA.getId(), org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(notifA).isNotEmpty();
        mvc.perform(get("/app/notificacoes/" + notifA.get(0).getId() + "/abrir").with(user(pB))).andExpect(status().isNotFound());

        // calendário (API JSON) também filtra pelo tenant
        var hoje = documentoService.hoje().plusDays(5);
        mvc.perform(get("/app/api/calendario?ano=" + hoje.getYear() + "&mes=" + hoje.getMonthValue()).with(user(pB)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));

        // e a empresa A continua vendo apenas os próprios dados
        ts.logar(adminA);
        assertThat(documentoService.listarTodos(new DocumentoFiltro())).extracting(Documento::getNome)
                .containsExactly("Documento secreto da empresa A");
    }
}
