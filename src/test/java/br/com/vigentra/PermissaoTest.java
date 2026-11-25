package br.com.vigentra;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.PermissaoService;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.web.dto.DocumentoForm;
import br.com.vigentra.service.conta.ConviteService;
import br.com.vigentra.web.dto.ConviteForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Controle de permissões e níveis de acesso")
class PermissaoTest extends IntegracaoBase {

    @Autowired DocumentoService documentoService;
    @Autowired UsuarioService usuarioService;
    @Autowired PermissaoService permissaoService;
    @Autowired ConviteService conviteService;

    @Test
    @DisplayName("Perfil Usuário: vê só os documentos sob sua responsabilidade e não acessa a administração")
    void perfilUsuario() throws Exception {
        Usuario admin = ts.novaEmpresa("Permissões");
        ts.logar(admin);
        Usuario comum = ts.usuario(PerfilCodigo.USUARIO);
        Documento doAdmin = ts.documento("Documento do admin", 40);
        // documento cujo responsável é o usuário comum (e-mail igual ao do responsável vinculado)
        DocumentoForm f = new DocumentoForm();
        f.setNome("Documento da Júlia");
        f.setCategoriaId(ts.categoria().getId());
        f.setDataVencimento(documentoService.hoje().plusDays(10));
        f.setResponsavelNome(comum.getNome());
        f.setResponsavelEmail(comum.getEmail());
        Documento daJulia = documentoService.salvar(f, null);

        var p = ts.logar(comum);
        assertThat(p.tem(PermissaoCodigo.DOC_VIEW_ALL)).isFalse();
        assertThat(documentoService.listarTodos(new DocumentoFiltro())).extracting(Documento::getNome)
                .containsExactly("Documento da Júlia");
        assertThatThrownBy(() -> documentoService.excluir(daJulia.getId())).isInstanceOf(AccessDeniedException.class);

        mvc.perform(get("/app/documentos/" + doAdmin.getId()).with(user(p))).andExpect(status().isNotFound());
        mvc.perform(get("/app/documentos/" + daJulia.getId()).with(user(p))).andExpect(status().isOk());
        mvc.perform(post("/app/documentos/" + daJulia.getId() + "/excluir").with(csrf()).with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/app/usuarios").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/app/atividades").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/app/relatorios").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(post("/app/configuracoes/alertas").with(csrf()).with(user(p)).param("diasAlerta", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Gestor gerencia documentos e usuários comuns, mas não cria administradores nem altera configurações")
    void perfilGestor() throws Exception {
        Usuario admin = ts.novaEmpresa("Gestor");
        ts.logar(admin);
        Usuario gestor = ts.usuario(PerfilCodigo.GESTOR);
        Usuario outroAdmin = ts.usuario(PerfilCodigo.ADMIN);

        var p = ts.logar(gestor);
        Documento d = ts.documento("Documento do gestor", 30);
        documentoService.excluir(d.getId()); // gestor pode excluir

        assertThat(usuarioService.perfisAtribuiveis()).containsExactly(PerfilCodigo.USUARIO);
        ConviteForm novoAdmin = new ConviteForm();
        novoAdmin.setEmail(TestSupport.unico("adm") + "@t.com");
        novoAdmin.setPerfil(PerfilCodigo.ADMIN);
        assertThatThrownBy(() -> conviteService.convidar(novoAdmin)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> usuarioService.alternarAtivo(outroAdmin.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> usuarioService.excluir(outroAdmin.getId())).isInstanceOf(AccessDeniedException.class);

        Usuario comum = ts.usuario(PerfilCodigo.USUARIO); // permitido
        assertThat(comum.getId()).isNotNull();

        mvc.perform(get("/app/usuarios").with(user(p))).andExpect(status().isOk());
        mvc.perform(post("/app/configuracoes/sistema").with(csrf()).with(user(p)).param("itensPorPagina", "20"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/app/usuarios/" + comum.getId() + "/excluir").with(csrf()).with(user(p))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Administrador ajusta a matriz de permissões; a empresa nunca fica sem administrador")
    void matrizEAdministrador() {
        Usuario admin = ts.novaEmpresa("Matriz");
        ts.logar(admin);
        Usuario comum = ts.usuario(PerfilCodigo.USUARIO);

        permissaoService.atualizar(PerfilCodigo.USUARIO, EnumSet.of(PermissaoCodigo.DOC_VIEW_ALL, PermissaoCodigo.REPORT_VIEW));
        var pComum = ts.principal(comum);
        assertThat(pComum.tem(PermissaoCodigo.DOC_VIEW_ALL)).isTrue();
        assertThat(pComum.tem(PermissaoCodigo.DOC_CREATE)).isFalse();

        assertThatThrownBy(() -> permissaoService.atualizar(PerfilCodigo.ADMIN, EnumSet.noneOf(PermissaoCodigo.class)))
                .isInstanceOf(NegocioException.class);

        // o único administrador não pode ser rebaixado/desativado por outro admin? -> cria 2º admin e tenta desativar o 1º
        Usuario admin2 = ts.usuario(PerfilCodigo.ADMIN);
        ts.logar(admin2);
        usuarioService.alternarAtivo(admin.getId()); // permitido: ainda resta admin2
        ts.logar(ts.recarregar(admin2));
        assertThatThrownBy(() -> usuarioService.excluir(admin2.getId())).isInstanceOf(NegocioException.class); // não exclui a si mesmo

        // usuário comum sem permissão de gestão não altera permissões
        ts.logar(comum);
        assertThatThrownBy(() -> permissaoService.atualizar(PerfilCodigo.GESTOR, EnumSet.allOf(PermissaoCodigo.class)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
