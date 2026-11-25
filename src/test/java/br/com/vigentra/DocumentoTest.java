package br.com.vigentra;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.repository.DocumentoRepository;
import br.com.vigentra.repository.LogAtividadeRepository;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.RecursoNaoEncontradoException;
import br.com.vigentra.web.dto.DocumentoForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Cadastro, edição, exclusão e consulta de documentos")
class DocumentoTest extends IntegracaoBase {

    @Autowired DocumentoService service;
    @Autowired DocumentoRepository repo;
    @Autowired LogAtividadeRepository logRepo;

    @Test
    @DisplayName("Cadastra pelo formulário web, com anexo PDF validado, e persiste no banco")
    void cadastroViaFormulario() throws Exception {
        Usuario admin = ts.novaEmpresa("Docs Web");
        var p = ts.logar(admin);
        Long categoriaId = ts.categoria().getId();
        ts.deslogar();

        var pdf = new MockMultipartFile("arquivo", "alvara.pdf", "application/pdf",
                "%PDF-1.4\n% conteúdo de teste".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/app/documentos").file(pdf).with(csrf()).with(user(p))
                        .param("nome", "Alvará de Funcionamento").param("categoriaId", categoriaId.toString())
                        .param("dataEmissao", "2026-01-10").param("dataVencimento", "2027-01-10")
                        .param("responsavelNome", "Fulano").param("responsavelEmail", "fulano@teste.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/app/documentos/*"));

        ts.logar(admin);
        Documento d = service.listarTodos(new DocumentoFiltro()).stream()
                .filter(x -> x.getNome().equals("Alvará de Funcionamento")).findFirst().orElseThrow();
        assertThat(d.temArquivo()).isTrue();
        assertThat(d.getArquivoNomeOriginal()).isEqualTo("alvara.pdf");
        assertThat(d.getArquivoArmazenado()).doesNotContain("alvara"); // nome aleatório no disco
        assertThat(d.getResponsavel().getEmail()).isEqualTo("fulano@teste.com");

        // download passa pela checagem de tenant
        mvc.perform(get("/app/documentos/" + d.getId() + "/arquivo").with(user(p)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Rejeita arquivo com conteúdo falso (executável renomeado para .pdf)")
    void rejeitaArquivoFalso() throws Exception {
        Usuario admin = ts.novaEmpresa("Upload");
        var p = ts.logar(admin);
        Long categoriaId = ts.categoria().getId();
        var falso = new MockMultipartFile("arquivo", "virus.pdf", "application/pdf", "MZ\u0090\u0000binario".getBytes(StandardCharsets.ISO_8859_1));
        mvc.perform(multipart("/app/documentos").file(falso).with(csrf()).with(user(p))
                        .param("nome", "Doc com arquivo falso").param("categoriaId", categoriaId.toString())
                        .param("dataVencimento", "2027-01-10"))
                .andExpect(status().isOk()); // volta ao formulário com erro
        ts.logar(admin);
        assertThat(service.listarTodos(new DocumentoFiltro()).stream().anyMatch(d -> d.getNome().equals("Doc com arquivo falso"))).isFalse();
    }

    @Test
    @DisplayName("Validações: vencimento obrigatório (ou 'sem vencimento') e vencimento após emissão")
    void validacoes() {
        ts.logar(ts.novaEmpresa("Validação"));
        DocumentoForm f = new DocumentoForm();
        f.setNome("Sem data");
        f.setCategoriaId(ts.categoria().getId());
        assertThatThrownBy(() -> service.salvar(f, null)).isInstanceOf(NegocioException.class).hasMessageContaining("vencimento");

        f.setDataEmissao(service.hoje().minusDays(1));
        f.setDataVencimento(service.hoje().minusDays(10));
        assertThatThrownBy(() -> service.salvar(f, null)).isInstanceOf(NegocioException.class).hasMessageContaining("anterior");

        f.setDataVencimento(null);
        f.setSemVencimento(true);
        assertThat(service.status(service.salvar(f, null))).isEqualTo(StatusDocumento.SEM_VENCIMENTO);
    }

    @Test
    @DisplayName("Edição carrega e altera os dados; exclusão remove e registra auditoria")
    void edicaoEExclusao() throws Exception {
        Usuario admin = ts.novaEmpresa("Edição");
        var p = ts.logar(admin);
        Documento d = ts.documento("CND Federal", 10);
        assertThat(service.status(d)).isEqualTo(StatusDocumento.PROXIMO_VENCIMENTO);

        DocumentoForm f = DocumentoForm.de(service.buscar(d.getId()));
        f.setNome("CND Federal (renovada)");
        f.setDataVencimento(service.hoje().plusDays(180));
        Documento editado = service.salvar(f, null);
        assertThat(editado.getId()).isEqualTo(d.getId());
        assertThat(service.status(editado)).isEqualTo(StatusDocumento.REGULAR);

        mvc.perform(get("/app/documentos/" + d.getId() + "/editar").with(user(p))).andExpect(status().isOk());

        mvc.perform(post("/app/documentos/" + d.getId() + "/excluir").with(csrf()).with(user(p)))
                .andExpect(status().is3xxRedirection());
        assertThat(repo.findById(d.getId())).isEmpty();
        ts.logar(admin);
        assertThatThrownBy(() -> service.buscar(d.getId())).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Pesquisa, filtro por situação/categoria e ordenação por vencimento")
    void pesquisaEFiltros() {
        ts.logar(ts.novaEmpresa("Filtros"));
        ts.documento("Contrato de locação", 200);
        ts.documento("Certificado digital A1", 3);
        ts.documento("AVCB bombeiros", -5);
        ts.documento("Contrato social", null);

        DocumentoFiltro f = new DocumentoFiltro();
        assertThat(service.listarTodos(f)).extracting(Documento::getNome)
                .containsExactly("AVCB bombeiros", "Certificado digital A1", "Contrato de locação", "Contrato social");

        f.setStatus(StatusDocumento.VENCIDO);
        assertThat(service.listarTodos(f)).extracting(Documento::getNome).containsExactly("AVCB bombeiros");

        f.setStatus(StatusDocumento.PROXIMO_VENCIMENTO);
        assertThat(service.listarTodos(f)).extracting(Documento::getNome).containsExactly("Certificado digital A1");

        f.setStatus(null);
        f.setQ("contrato");
        assertThat(service.listarTodos(f)).hasSize(2);

        f.setQ(null);
        f.setVencimentoDe(service.hoje());
        f.setVencimentoAte(service.hoje().plusDays(30));
        assertThat(service.listarTodos(f)).extracting(Documento::getNome).containsExactly("Certificado digital A1");

        var contagem = service.contagemPorStatus();
        assertThat(contagem.get(StatusDocumento.REGULAR)).isEqualTo(1);
        assertThat(contagem.get(StatusDocumento.VENCIDO)).isEqualTo(1);
        assertThat(contagem.get(StatusDocumento.SEM_VENCIMENTO)).isEqualTo(1);
    }
}
