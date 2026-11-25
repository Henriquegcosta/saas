package br.com.vigentra;

import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.UsuarioPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Estados vazios do painel da plataforma (/plataforma/comunicacao).
 * O painel mostra envios de TODAS as empresas e o banco H2 é compartilhado entre os testes,
 * por isso a situação "sem nenhum envio na plataforma" não é reproduzível aqui; testam-se B e C.
 */
@DisplayName("Plataforma: mensagens enviadas e filtros sem resultado")
@TestPropertySource(properties = "app.platform-admins=operador-vazios@plataforma.test")
class PlataformaVaziosTest extends IntegracaoBase {

    @Autowired UsuarioRepository usuarioRepo;

    private UsuarioPrincipal operador() {
        Usuario op = usuarioRepo.findByEmailIgnoreCase("operador-vazios@plataforma.test").orElseGet(() -> {
            Usuario u = ts.novaEmpresaNaoConfirmada("Operação Plataforma", "operador-vazios@plataforma.test");
            u.setEmailVerificadoEm(java.time.LocalDateTime.now());
            return ts.salvar(u);
        });
        UsuarioPrincipal p = ts.principal(op);
        assertThat(p.isPlataforma()).isTrue();
        return p;
    }

    private String html(UsuarioPrincipal p, String url) throws Exception {
        return mvc.perform(get(url).with(user(p))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("B: empresa com envios (e-mail de confirmação do cadastro) mostra a lista, sem mensagem")
    void comEnvios() throws Exception {
        Usuario cliente = ts.novaEmpresa("Cliente Plataforma");
        String h = html(operador(), "/plataforma/comunicacao?empresaId=" + cliente.getEmpresa().getId());
        assertThat(h).contains("Confirmação de e-mail")
                .doesNotContain("Nenhuma mensagem encontrada").doesNotContain("Nenhuma mensagem enviada");
    }

    @Test
    @DisplayName("C: filtros sem resultado mostram 'Nenhuma mensagem encontrada' e Limpar filtros")
    void filtroSemResultado() throws Exception {
        Usuario cliente = ts.novaEmpresa("Cliente Sem WhatsApp");
        String h = html(operador(), "/plataforma/comunicacao?empresaId=" + cliente.getEmpresa().getId() + "&canal=WHATSAPP");
        assertThat(h).contains("Nenhuma mensagem encontrada", "<i class=\"bi bi-x-circle me-1\"></i>Limpar filtros")
                .doesNotContain("Nenhuma mensagem enviada");
    }
}
