package br.com.vigentra;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.model.Convite;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.ConviteRepository;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.LimitadorTaxa;
import br.com.vigentra.security.UsuarioDetailsService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.RecuperacaoSenhaService;
import br.com.vigentra.service.comunicacao.MensagemContaEvent;
import br.com.vigentra.service.conta.ContaService;
import br.com.vigentra.service.conta.ConviteService;
import br.com.vigentra.web.dto.AceiteConviteForm;
import br.com.vigentra.web.dto.ConviteForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("Jornada da conta: confirmação, boas-vindas, convites, senha e troca de e-mail")
@RecordApplicationEvents
class JornadaContaTest extends IntegracaoBase {

    @Autowired ApplicationEvents eventos;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired ResponsavelRepository responsavelRepo;
    @Autowired ConviteRepository conviteRepo;
    @Autowired HistoricoEnvioRepository historicoRepo;
    @Autowired UsuarioDetailsService uds;
    @Autowired ContaService contaService;
    @Autowired ConviteService conviteService;
    @Autowired RecuperacaoSenhaService recuperacaoService;
    @Autowired LimitadorTaxa limitador;

    @BeforeEach
    void limpar() {
        limitador.limpar();
    }

    private List<MensagemContaEvent> mensagens(TipoMensagem tipo, String para) {
        return eventos.stream(MensagemContaEvent.class).filter(e -> e.tipo() == tipo && e.para().equals(para)).toList();
    }

    private static String token(MensagemContaEvent e) {
        String link = (String) e.variaveis().get("link");
        return link.substring(link.indexOf("token=") + 6);
    }

    @Test
    @DisplayName("Cadastro envia confirmação; conta não verificada não acessa o sistema; boas-vindas só após confirmar; token de uso único")
    void confirmacaoDeEmail() throws Exception {
        String email = TestSupport.unico("conf") + "@empresa.com.br";
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Paula Andrade").param("empresa", "Clínica Confirmação")
                        .param("email", email).param("senha", "Forte@2026").param("confirmacaoSenha", "Forte@2026")
                        .param("aceiteTermos", "true"))
                .andExpect(redirectedUrl("/login"));

        Usuario u = usuarioRepo.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(u.isEmailVerificado()).isFalse();
        assertThat(mensagens(TipoMensagem.CONFIRMACAO_EMAIL, email)).hasSize(1);
        assertThat(mensagens(TipoMensagem.BOAS_VINDAS, email)).isEmpty();          // ainda não!
        String token = token(mensagens(TipoMensagem.CONFIRMACAO_EMAIL, email).get(0));
        // o token não fica no banco em texto puro, só o hash
        assertThat(token).hasSizeGreaterThan(40);

        // pode fazer login, mas é levado à tela de confirmação
        mvc.perform(formLogin("/login").user(email).password("Forte@2026")).andExpect(redirectedUrl("/app/dashboard"));
        var pendente = uds.loadUserByUsername(email);
        mvc.perform(get("/app/dashboard").with(user(pendente))).andExpect(redirectedUrl("/conta/confirmar-email"));
        mvc.perform(get("/app/documentos").with(user(pendente))).andExpect(redirectedUrl("/conta/confirmar-email"));
        mvc.perform(get("/app/api/calendario?ano=2026&mes=1").with(user(pendente))).andExpect(status().isForbidden());
        mvc.perform(get("/conta/confirmar-email").with(user(pendente))).andExpect(status().isOk());

        // abrir o link NÃO confirma (protege contra robôs que abrem links); o POST confirma
        mvc.perform(get("/confirmar-email").param("token", token)).andExpect(model().attribute("tokenValido", true));
        assertThat(usuarioRepo.findById(u.getId()).orElseThrow().isEmailVerificado()).isFalse();
        mvc.perform(post("/confirmar-email").with(csrf()).param("token", token)).andExpect(redirectedUrl("/login"));

        u = usuarioRepo.findById(u.getId()).orElseThrow();
        assertThat(u.isEmailVerificado()).isTrue();
        assertThat(mensagens(TipoMensagem.BOAS_VINDAS, email)).hasSize(1);
        mvc.perform(get("/app/dashboard").with(user(uds.loadUserByUsername(email)))).andExpect(status().isOk());

        // reutilizar o link é recusado
        mvc.perform(post("/confirmar-email").with(csrf()).param("token", token)).andExpect(redirectedUrl("/confirmar-email"));
        assertThat(mensagens(TipoMensagem.BOAS_VINDAS, email)).hasSize(1);
        mvc.perform(get("/confirmar-email").param("token", token)).andExpect(model().attribute("tokenValido", false));

        // os dois e-mails ficaram no histórico da empresa (modo desenvolvimento = não enviados)
        var hist = historicoRepo.findRecentes(u.getEmpresa().getId(), PageRequest.of(0, 20));
        assertThat(hist).extracting(h -> h.getTipoMensagem()).contains(TipoMensagem.CONFIRMACAO_EMAIL, TipoMensagem.BOAS_VINDAS);
        assertThat(hist).allMatch(h -> h.getStatus() == StatusEnvio.MODO_DEV && h.getCanal() == CanalEnvio.EMAIL);
    }

    @Test
    @DisplayName("Reenvio de confirmação invalida o link anterior e é limitado")
    void reenvioDeConfirmacao() throws Exception {
        String email = TestSupport.unico("reenvio") + "@empresa.com.br";
        Usuario u = ts.novaEmpresaNaoConfirmada("Reenvio", email);
        String antigo = token(mensagens(TipoMensagem.CONFIRMACAO_EMAIL, email).get(0));
        var p = ts.logar(u);
        mvc.perform(post("/conta/confirmar-email/reenviar").with(csrf()).with(user(p))).andExpect(redirectedUrl("/conta/confirmar-email"));
        assertThat(mensagens(TipoMensagem.CONFIRMACAO_EMAIL, email)).hasSize(2);
        String novo = token(mensagens(TipoMensagem.CONFIRMACAO_EMAIL, email).get(1));
        assertThatThrownBy(() -> contaService.confirmar(antigo)).isInstanceOf(NegocioException.class);
        ts.logar(u);
        assertThatThrownBy(() -> contaService.reenviarConfirmacao()).isInstanceOf(NegocioException.class); // 1 por minuto
        assertThat(contaService.confirmar(novo).isEmailVerificado()).isTrue();
    }

    @Test
    @DisplayName("Convite: link seguro, empresa e perfil corretos, uso único, expiração e cancelamento")
    void convites() throws Exception {
        Usuario admin = ts.novaEmpresa("Convites");
        ts.logar(admin);
        ConviteForm f = new ConviteForm();
        String email = TestSupport.unico("convidado") + "@empresa.com.br";
        f.setEmail(email);
        f.setNome("Carla Souza");
        f.setPerfil(PerfilCodigo.GESTOR);
        var criado = conviteService.convidar(f);
        ts.deslogar();
        assertThat(mensagens(TipoMensagem.CONVITE, email)).hasSize(1);
        String token = token(mensagens(TipoMensagem.CONVITE, email).get(0));
        assertThat(token).isEqualTo(criado.token());
        assertThat(criado.convite().getTokenHash()).isNotEqualTo(token).hasSize(64);

        mvc.perform(get("/convite").param("token", token)).andExpect(status().isOk())
                .andExpect(model().attributeExists("convite"));
        mvc.perform(post("/convite").with(csrf()).param("token", token).param("nome", "Carla Souza")
                        .param("senha", "Forte@2026").param("confirmacaoSenha", "Forte@2026").param("aceiteTermos", "true"))
                .andExpect(redirectedUrl("/login"));

        Usuario novo = usuarioRepo.findByEmailComEmpresa(email).orElseThrow();
        assertThat(novo.getEmpresa().getId()).isEqualTo(admin.getEmpresa().getId());
        assertThat(novo.getPerfil().getCodigo()).isEqualTo(PerfilCodigo.GESTOR);
        assertThat(novo.isEmailVerificado()).isTrue();
        assertThat(responsavelRepo.findByUsuarioId(novo.getId())).hasSize(1);
        assertThat(mensagens(TipoMensagem.BOAS_VINDAS, email)).hasSize(1);
        mvc.perform(formLogin("/login").user(email).password("Forte@2026")).andExpect(redirectedUrl("/app/dashboard"));

        // uso único
        mvc.perform(post("/convite").with(csrf()).param("token", token).param("nome", "Outra Pessoa")
                        .param("senha", "Forte@2026").param("confirmacaoSenha", "Forte@2026").param("aceiteTermos", "true"))
                .andExpect(status().isOk()).andExpect(view().name("auth/convite")).andExpect(model().attribute("convite", (Object) null));

        // expirado e cancelado
        ts.logar(admin);
        f.setEmail(TestSupport.unico("exp") + "@empresa.com.br");
        var expirado = conviteService.convidar(f);
        Convite c = conviteRepo.findById(expirado.convite().getId()).orElseThrow();
        c.setExpiraEm(LocalDateTime.now().minusMinutes(1));
        conviteRepo.save(c);
        assertThat(conviteService.validar(expirado.token())).isEmpty();
        f.setEmail(TestSupport.unico("canc") + "@empresa.com.br");
        var cancelado = conviteService.convidar(f);
        conviteService.cancelar(cancelado.convite().getId());
        assertThat(conviteService.validar(cancelado.token())).isEmpty();

        // reenviar gera link novo e invalida o anterior
        f.setEmail(TestSupport.unico("reenv") + "@empresa.com.br");
        var primeiro = conviteService.convidar(f);
        var segundo = conviteService.reenviar(primeiro.convite().getId());
        assertThat(conviteService.validar(primeiro.token())).isEmpty();
        assertThat(conviteService.validar(segundo.token())).isPresent();

        // e-mail que já tem conta
        f.setEmail(admin.getEmail());
        assertThatThrownBy(() -> conviteService.convidar(f)).isInstanceOf(NegocioException.class);
    }

    @Test
    @DisplayName("Convite respeita a hierarquia: usuário comum não convida; gestor não convida administrador")
    void hierarquiaDoConvite() {
        Usuario admin = ts.novaEmpresa("Hierarquia convite");
        ts.logar(admin);
        Usuario comum = ts.usuario(PerfilCodigo.USUARIO);
        Usuario gestor = ts.usuario(PerfilCodigo.GESTOR);
        ConviteForm f = new ConviteForm();
        f.setEmail(TestSupport.unico("x") + "@empresa.com.br");
        f.setPerfil(PerfilCodigo.USUARIO);
        ts.logar(comum);
        assertThatThrownBy(() -> conviteService.convidar(f)).isInstanceOf(AccessDeniedException.class);
        ts.logar(gestor);
        f.setPerfil(PerfilCodigo.ADMIN);
        assertThatThrownBy(() -> conviteService.convidar(f)).isInstanceOf(AccessDeniedException.class);
        f.setPerfil(PerfilCodigo.USUARIO);
        assertThat(conviteService.convidar(f).convite().getEmpresa().getId()).isEqualTo(admin.getEmpresa().getId());
    }

    @Test
    @DisplayName("Recuperação de senha: link de uso único e aviso de senha alterada")
    void recuperacaoDeSenha() throws Exception {
        Usuario u = ts.novaEmpresa("Recuperação");
        recuperacaoService.solicitar(u.getEmail());
        recuperacaoService.solicitar("nao-existe@empresa.com.br");   // resposta igual, nada enviado
        assertThat(eventos.stream(MensagemContaEvent.class).filter(e -> e.para().equals("nao-existe@empresa.com.br"))).isEmpty();
        String token = token(mensagens(TipoMensagem.RECUPERACAO_SENHA, u.getEmail()).get(0));

        mvc.perform(post("/redefinir-senha").with(csrf()).param("token", token)
                        .param("novaSenha", "NovaSenha@1").param("confirmacao", "NovaSenha@1"))
                .andExpect(redirectedUrl("/login"));
        assertThat(mensagens(TipoMensagem.SENHA_ALTERADA, u.getEmail())).hasSize(1);
        mvc.perform(formLogin("/login").user(u.getEmail()).password("NovaSenha@1")).andExpect(redirectedUrl("/app/dashboard"));
        assertThatThrownBy(() -> recuperacaoService.redefinir(token, "Outra@1234", "Outra@1234")).isInstanceOf(NegocioException.class);
    }

    @Test
    @DisplayName("Troca do e-mail de login exige confirmação no novo endereço")
    void trocaDeEmail() {
        Usuario u = ts.novaEmpresa("Troca de e-mail");
        ts.logar(u);
        String novo = TestSupport.unico("novo") + "@empresa.com.br";
        contaService.solicitarAlteracaoEmail(novo);
        assertThat(usuarioRepo.findById(u.getId()).orElseThrow().getEmail()).isEqualTo(u.getEmail()); // ainda vale o antigo
        String token = token(mensagens(TipoMensagem.ALTERACAO_EMAIL, novo).get(0));
        Usuario alterado = contaService.confirmarAlteracaoEmail(token);
        assertThat(alterado.getEmail()).isEqualTo(novo);
        assertThat(alterado.getEmailPendente()).isNull();
        assertThat(responsavelRepo.findByUsuarioId(u.getId())).allMatch(r -> r.getEmail().equals(novo));
        assertThatThrownBy(() -> contaService.confirmarAlteracaoEmail(token)).isInstanceOf(NegocioException.class);
    }
}
