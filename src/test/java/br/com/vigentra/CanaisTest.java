package br.com.vigentra;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.HistoricoEnvio;
import br.com.vigentra.domain.model.Responsavel;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.ResponsavelService;
import br.com.vigentra.service.alerta.AlertaService;
import br.com.vigentra.service.alerta.ResultadoRotina;
import br.com.vigentra.service.comunicacao.LinkPreferenciasService;
import br.com.vigentra.service.comunicacao.LinkPreferenciasService.Alvo;
import br.com.vigentra.service.comunicacao.PreferenciasService;
import br.com.vigentra.service.comunicacao.RegraCanalService;
import br.com.vigentra.web.dto.PreferenciasForm;
import br.com.vigentra.web.dto.ResponsavelForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Notificações multicanal (e-mail, WhatsApp, SMS), consentimento, webhook e plataforma")
@TestPropertySource(properties = {
        "app.whatsapp.dev-mode=true",
        "app.whatsapp.app-secret=segredo-do-app-meta",
        "app.whatsapp.webhook-verify-token=verifica-123",
        "app.platform-admins=operador@plataforma.test"})
class CanaisTest extends IntegracaoBase {

    @Autowired AlertaService alertaService;
    @Autowired DocumentoService documentoService;
    @Autowired PreferenciasService preferenciasService;
    @Autowired ResponsavelService responsavelService;
    @Autowired RegraCanalService regraCanalService;
    @Autowired LinkPreferenciasService links;
    @Autowired HistoricoEnvioRepository historicoRepo;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired ResponsavelRepository responsavelRepo;

    private List<HistoricoEnvio> alertas(Long empresaId) {
        return historicoRepo.findRecentes(empresaId, PageRequest.of(0, 500)).stream()
                .filter(h -> h.getTipoMensagem() == TipoMensagem.ALERTA_VENCIMENTO).toList();
    }

    private static PreferenciasForm whatsapp(String celular) {
        PreferenciasForm f = new PreferenciasForm();
        f.setCelular(celular);
        f.setReceberEmail(true);
        f.setReceberWhatsapp(true);
        f.setConsentimentoWhatsapp(true);
        return f;
    }

    @Test
    @DisplayName("Régua multicanal: WhatsApp só a partir de 7 dias e só com consentimento; sem duplicidade por canal; SMS nunca registrado")
    void reguaMulticanal() {
        Usuario admin = ts.novaEmpresa("Multicanal");
        ts.logar(admin);
        preferenciasService.salvarMinhas(whatsapp("(11) 98888-7777"));
        Documento urgente = ts.documento("Certificado digital", 5);   // período D7: e-mail + WhatsApp
        Documento distante = ts.documento("Licença ambiental", 20);   // período D30: só e-mail
        Long empresaId = admin.getEmpresa().getId();
        // mesmo habilitando SMS na regra, o canal está indisponível e não pode gerar registros
        regraCanalService.salvar(Map.of("D7", Set.of(CanalEnvio.EMAIL, CanalEnvio.WHATSAPP, CanalEnvio.SMS),
                "D30", Set.of(CanalEnvio.EMAIL)));
        ts.deslogar();

        ResultadoRotina r = alertaService.executarEmpresa(empresaId, documentoService.hoje());
        List<HistoricoEnvio> h = alertas(empresaId);
        // e-mail: responsável + admin, nos dois documentos = 4 ; WhatsApp: só o admin (o responsável não autorizou) = 1
        assertThat(h).filteredOn(x -> x.getCanal() == CanalEnvio.EMAIL).hasSize(4);
        assertThat(h).filteredOn(x -> x.getCanal() == CanalEnvio.WHATSAPP).singleElement().satisfies(x -> {
            assertThat(x.getDocumento().getId()).isEqualTo(urgente.getId());
            assertThat(x.getDestinatario()).isEqualTo("+5511988887777");
            assertThat(x.getStatus()).isEqualTo(StatusEnvio.MODO_DEV);   // modo desenvolvimento: NÃO enviado
            assertThat(x.getProvedor()).isEqualTo("whatsapp-dev");
        });
        assertThat(h).noneMatch(x -> x.getCanal() == CanalEnvio.SMS);
        assertThat(h).filteredOn(x -> x.getDocumento().getId().equals(distante.getId())).allMatch(x -> x.getCanal() == CanalEnvio.EMAIL);
        assertThat(r.mensagensWhatsapp()).isEqualTo(1);

        // nova execução (ex.: agendador rodando de novo): nada duplicado em nenhum canal
        ResultadoRotina r2 = alertaService.executarEmpresa(empresaId, documentoService.hoje());
        assertThat(r2.duplicadosIgnorados()).isEqualTo(5);
        assertThat(alertas(empresaId)).hasSize(5);
    }

    @Test
    @DisplayName("Regra da empresa decide os canais por prazo")
    void regraPorPrazo() {
        Usuario admin = ts.novaEmpresa("Regra canais");
        ts.logar(admin);
        preferenciasService.salvarMinhas(whatsapp("11977776666"));
        regraCanalService.salvar(Map.of("D7", Set.of(CanalEnvio.WHATSAPP)));   // no prazo de 7 dias, só WhatsApp
        ts.documento("Alvará", 3);
        ts.deslogar();
        alertaService.executarEmpresa(admin.getEmpresa().getId(), documentoService.hoje());
        assertThat(alertas(admin.getEmpresa().getId())).singleElement()
                .satisfies(x -> assertThat(x.getCanal()).isEqualTo(CanalEnvio.WHATSAPP));
    }

    @Test
    @DisplayName("Consentimento: obrigatório para WhatsApp, registra origem e é revogado ao trocar o celular")
    void consentimento() {
        Usuario admin = ts.novaEmpresa("Consentimento");
        ts.logar(admin);
        PreferenciasForm semAutorizacao = whatsapp("11966665555");
        semAutorizacao.setConsentimentoWhatsapp(false);
        assertThatThrownBy(() -> preferenciasService.salvarMinhas(semAutorizacao)).isInstanceOf(NegocioException.class)
                .hasMessageContaining("autorização");
        PreferenciasForm semCelular = whatsapp(null);
        assertThatThrownBy(() -> preferenciasService.salvarMinhas(semCelular)).isInstanceOf(NegocioException.class);
        PreferenciasForm invalido = whatsapp("1234");
        assertThatThrownBy(() -> preferenciasService.salvarMinhas(invalido)).isInstanceOf(NegocioException.class);

        preferenciasService.salvarMinhas(whatsapp("11966665555"));
        Usuario u = usuarioRepo.findById(admin.getId()).orElseThrow();
        assertThat(u.getPreferencias().aceita(CanalEnvio.WHATSAPP)).isTrue();
        assertThat(u.getPreferencias().getWhatsappConsentimentoOrigem()).contains("Próprio usuário");

        // trocar o número sem reautorizar revoga
        PreferenciasForm outroNumero = whatsapp("11955554444");
        outroNumero.setReceberWhatsapp(false);
        outroNumero.setConsentimentoWhatsapp(false);
        preferenciasService.salvarMinhas(outroNumero);
        u = usuarioRepo.findById(admin.getId()).orElseThrow();
        assertThat(u.getPreferencias().getWhatsappConsentimentoEm()).isNull();
        assertThat(u.getCelular()).isEqualTo("+5511955554444");

        // gestor registrando por um responsável: a origem identifica quem declarou
        ResponsavelForm rf = new ResponsavelForm();
        rf.setNome("Responsável Externo");
        rf.setEmail(TestSupport.unico("ext") + "@empresa.com.br");
        rf.setPreferencias(whatsapp("11944443333"));
        Responsavel r = responsavelService.salvar(rf);
        assertThat(responsavelRepo.findById(r.getId()).orElseThrow().getPreferencias().getWhatsappConsentimentoOrigem())
                .contains("Registrado por").contains(admin.getEmail());
    }

    @Test
    @DisplayName("Links assinados de preferências e descadastro (sem login); assinatura adulterada é recusada")
    void linksDePreferencias() throws Exception {
        Usuario admin = ts.novaEmpresa("Links");
        ts.logar(admin);
        Documento d = ts.documento("Contrato", 40);
        Responsavel r = d.getResponsavel();
        ts.deslogar();

        String link = links.linkPreferencias(Alvo.R, r.getId(), r.getEmail()).replace("http://localhost:8080", "");
        mvc.perform(get(link)).andExpect(status().isOk()).andExpect(content().string(containsString(r.getNome())));
        mvc.perform(get(link.replaceAll("t=[^&]+", "t=adulterado1234567890adulterado12"))).andExpect(status().isNotFound());
        mvc.perform(get("/preferencias?a=R&id=" + (r.getId() + 1) + "&t=" + links.assinar(Alvo.R, r.getId(), r.getEmail())))
                .andExpect(status().isNotFound());

        // abrir o link de descadastro só mostra a confirmação
        String desc = links.linkDescadastro(Alvo.R, r.getId(), r.getEmail()).replace("http://localhost:8080", "");
        mvc.perform(get(desc)).andExpect(status().isOk());
        assertThat(responsavelRepo.findById(r.getId()).orElseThrow().getPreferencias().isReceberEmail()).isTrue();
        // "One-Click" do cliente de e-mail (RFC 8058): POST sem sessão e sem CSRF
        mvc.perform(post(desc).contentType(MediaType.APPLICATION_FORM_URLENCODED).content("List-Unsubscribe=One-Click"))
                .andExpect(status().isOk());
        assertThat(responsavelRepo.findById(r.getId()).orElseThrow().getPreferencias().isReceberEmail()).isFalse();

        // descadastrado não recebe mais e-mail de alerta
        alertaService.executarEmpresa(admin.getEmpresa().getId(), documentoService.hoje().plusDays(20));
        assertThat(alertas(admin.getEmpresa().getId())).noneMatch(x -> x.getDestinatario().equalsIgnoreCase(r.getEmail()));
    }

    private static String assinar(String corpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo-do-app-meta".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Webhook oficial da Meta: verificação, assinatura HMAC, status de entrega e descadastro por \"SAIR\"")
    void webhookWhatsApp() throws Exception {
        mvc.perform(get("/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=verifica-123&hub.challenge=4242"))
                .andExpect(status().isOk()).andExpect(content().string("4242"));
        mvc.perform(get("/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=errado&hub.challenge=4242"))
                .andExpect(status().isForbidden());

        Usuario admin = ts.novaEmpresa("Webhook");
        ts.logar(admin);
        preferenciasService.salvarMinhas(whatsapp("11933332222"));
        ts.documento("CND", 2);
        ts.deslogar();
        alertaService.executarEmpresa(admin.getEmpresa().getId(), documentoService.hoje());
        HistoricoEnvio w = alertas(admin.getEmpresa().getId()).stream().filter(x -> x.getCanal() == CanalEnvio.WHATSAPP).findFirst().orElseThrow();
        w.setIdExterno("wamid.TESTE123");
        historicoRepo.save(w);

        String status = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.TESTE123\",\"status\":\"delivered\"}]}}]}]}";
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(status))
                .andExpect(status().isUnauthorized());                                   // sem assinatura
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(status)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized());                                   // assinatura falsa
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(status)
                        .header("X-Hub-Signature-256", assinar(status)))
                .andExpect(status().isOk());
        assertThat(historicoRepo.findById(w.getId()).orElseThrow().getStatus()).isEqualTo(StatusEnvio.ENTREGUE);

        String sair = "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"from\":\"5511933332222\",\"type\":\"text\",\"text\":{\"body\":\"Sair\"}}]}}]}]}";
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(sair)
                .header("X-Hub-Signature-256", assinar(sair))).andExpect(status().isOk());
        assertThat(usuarioRepo.findById(admin.getId()).orElseThrow().getPreferencias().aceita(CanalEnvio.WHATSAPP)).isFalse();
    }

    @Test
    @DisplayName("Painel da plataforma: exclusivo do operador, mostra todas as empresas com destinatários mascarados")
    void painelDaPlataforma() throws Exception {
        Usuario clienteA = ts.novaEmpresa("Cliente A");
        ts.logar(clienteA);
        ts.documento("Documento cliente A", 3);
        ts.deslogar();
        alertaService.executarEmpresa(clienteA.getEmpresa().getId(), documentoService.hoje());

        Usuario operador = usuarioRepo.findByEmailIgnoreCase("operador@plataforma.test").orElseGet(() -> {
            Usuario u = ts.novaEmpresaNaoConfirmada("VIGENTRA Operações", "operador@plataforma.test");
            u.setEmailVerificadoEm(java.time.LocalDateTime.now());
            return ts.salvar(u);
        });
        var pOperador = ts.principal(operador);
        assertThat(pOperador.isPlataforma()).isTrue();

        mvc.perform(get("/plataforma/comunicacao").with(user(ts.principal(clienteA)))).andExpect(status().isForbidden());
        mvc.perform(get("/plataforma/comunicacao").with(user(pOperador)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cliente A")))
                .andExpect(content().string(not(containsString(clienteA.getEmail()))))       // e-mail completo não aparece
                .andExpect(content().string(containsString(clienteA.getEmail().substring(0, 2) + "***@")));
        mvc.perform(get("/plataforma/comunicacao?empresaId=" + clienteA.getEmpresa().getId() + "&canal=EMAIL").with(user(pOperador)))
                .andExpect(status().isOk());
    }
}
