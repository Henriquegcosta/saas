package br.com.vigentra.unit;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.service.comunicacao.email.BrevoEmailProvider;
import br.com.vigentra.service.comunicacao.email.DevEmailProvider;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.comunicacao.email.MensagemEmail;
import br.com.vigentra.service.comunicacao.email.SmtpEmailProvider;
import br.com.vigentra.service.comunicacao.whatsapp.WhatsAppCloudProvider;
import br.com.vigentra.service.comunicacao.whatsapp.WhatsAppDevProvider;
import br.com.vigentra.service.comunicacao.whatsapp.WhatsAppService;
import br.com.vigentra.util.Mascaras;
import br.com.vigentra.util.TelefoneUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Integrações HTTP testadas contra um servidor SIMULADO (MockRestServiceServer): nenhuma chamada real. */
class ProvedoresTest {

    private final ObjectMapper mapper = new ObjectMapper();

    static AppProperties props(String brevoKey, String waToken, String waPhoneId) {
        return new AppProperties("VIGENTRA", "https://app.vigentra.test", "America/Sao_Paulo", "k", "s", "",
                new AppProperties.Storage("target/x", List.of("pdf")),
                new AppProperties.Mail("auto", "nao-responda@vigentra.test", "VIGENTRA", "target/test-emails-unit", 3, 60,
                        new AppProperties.Brevo(brevoKey, "https://api.brevo.test/v3")),
                new AppProperties.Whatsapp(waToken, waPhoneId, null, "https://graph.test", "v23.0", "segredo", "verifica",
                        false, "target/test-wa-unit", "pt_BR", "vigentra_vencimento_proximo", "vigentra_documento_vencido",
                        "hello_world", "en_US"),
                new AppProperties.Sms("nenhum"), new AppProperties.Comunicacao(false),
                new AppProperties.Alerts("0 0 7 * * *", "0 15 * * * *", false),
                new AppProperties.Security(5, 15, 20, 30));
    }

    @SuppressWarnings("unchecked")
    private EmailService emailService(AppProperties p, RestClient.Builder b) {
        return new EmailService(new BrevoEmailProvider(p, b, mapper),
                new SmtpEmailProvider(mock(ObjectProvider.class), new MailProperties(), p), new DevEmailProvider(p), p);
    }

    @Nested
    @DisplayName("E-mail via API da Brevo")
    class Brevo {

        @Test
        void enviaComCabecalhosDeDescadastro() {
            RestClient.Builder b = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
            server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("api-key", "chave-brevo"))
                    .andExpect(jsonPath("$.sender.email").value("nao-responda@vigentra.test"))
                    .andExpect(jsonPath("$.to[0].email").value("ana@clinica.com.br"))
                    .andExpect(jsonPath("$.subject").value("Assunto"))
                    .andExpect(jsonPath("$.headers['List-Unsubscribe']").value("<https://app.vigentra.test/descadastrar>"))
                    .andExpect(jsonPath("$.headers['List-Unsubscribe-Post']").value("List-Unsubscribe=One-Click"))
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"messageId\":\"<202611140001.abc@smtp-relay.brevo.com>\"}"));
            EmailService email = emailService(props("chave-brevo", null, null), b);
            assertThat(email.getProvedor()).isEqualTo("brevo");
            ResultadoEnvio r = email.enviar(new MensagemEmail("ana@clinica.com.br", "Ana", "Clínica", null, "Assunto", "<p>oi</p>",
                    "https://app.vigentra.test/descadastrar", "alerta"));
            assertThat(r.status()).isEqualTo(StatusEnvio.ENVIADO);
            assertThat(r.idExterno()).contains("brevo.com");
            server.verify();
        }

        @Test
        void erroDaApiViraFalhaLegivelSemExporAChave() {
            RestClient.Builder b = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
            server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));
            ResultadoEnvio r = emailService(props("chave-secreta-123", null, null), b)
                    .enviar(new MensagemEmail("ana@clinica.com.br", "Ana", null, null, "A", "<p/>", null, null));
            assertThat(r.status()).isEqualTo(StatusEnvio.FALHA);
            assertThat(r.erro()).contains("401").contains("Key not found").doesNotContain("chave-secreta-123");
        }

        @Test
        void enderecosDeDemonstracaoNaoSaoEnviadosAoProvedor() {
            RestClient.Builder b = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();   // nenhuma requisição esperada
            EmailService email = emailService(props("chave", null, null), b);
            for (String demo : List.of("admin@vidaplena.demo", "teste@example.com", "x@empresa.test", "y@foo.invalid")) {
                assertThat(email.enviar(new MensagemEmail(demo, null, null, null, "A", "<p/>", null, null)).status())
                        .isEqualTo(StatusEnvio.IGNORADO);
            }
            server.verify();
        }

        @Test
        void semCredenciaisUsaModoDesenvolvimento() {
            EmailService email = emailService(props("", null, null), RestClient.builder());
            assertThat(email.isModoDesenvolvimento()).isTrue();
            assertThat(email.enviar(new MensagemEmail("a@b.com.br", null, null, null, "A", "<p/>", null, null)).status())
                    .isEqualTo(StatusEnvio.MODO_DEV);
        }
    }

    @Nested
    @DisplayName("WhatsApp Business Cloud API (Meta)")
    class WhatsApp {

        @Test
        void enviaTemplateAprovadoComParametros() {
            AppProperties p = props(null, "token-meta", "1234567890");
            RestClient.Builder b = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
            server.expect(requestTo("https://graph.test/v23.0/1234567890/messages"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer token-meta"))
                    .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                    .andExpect(jsonPath("$.to").value("5511999990001"))
                    .andExpect(jsonPath("$.type").value("template"))
                    .andExpect(jsonPath("$.template.name").value("vigentra_documento_vencido"))
                    .andExpect(jsonPath("$.template.language.code").value("pt_BR"))
                    .andExpect(jsonPath("$.template.components[0].parameters[1].text").value("Alvará de Funcionamento"))
                    .andRespond(withSuccess("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.HBgN\"}]}",
                            MediaType.APPLICATION_JSON));
            WhatsAppService wa = new WhatsAppService(new WhatsAppCloudProvider(p, b, mapper), new WhatsAppDevProvider(p, mapper), p);
            ResultadoEnvio r = wa.enviar(wa.alerta("+5511999990001", TipoAlerta.VENCIDO, "Ana", "Alvará de Funcionamento",
                    "10/11/2026", "Vencido há 4 dias", "Clínica"));
            assertThat(r.status()).isEqualTo(StatusEnvio.ENVIADO);
            assertThat(r.idExterno()).isEqualTo("wamid.HBgN");
            server.verify();
        }

        @Test
        void erroDaMetaRegistrado() {
            AppProperties p = props(null, "token-meta", "1234567890");
            RestClient.Builder b = RestClient.builder();
            MockRestServiceServer.bindTo(b).build().expect(requestTo("https://graph.test/v23.0/1234567890/messages"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"message\":\"Template name does not exist in the translation\",\"code\":132001}}"));
            ResultadoEnvio r = new WhatsAppCloudProvider(p, b, mapper)
                    .enviar(new br.com.vigentra.service.comunicacao.whatsapp.MensagemWhatsApp("+5511999990001", "x", "pt_BR", List.of(), ""));
            assertThat(r.status()).isEqualTo(StatusEnvio.FALHA);
            assertThat(r.erro()).contains("132001").contains("Template name");
        }

        @Test
        void semCredenciaisCanalNaoConfigurado() {
            AppProperties p = props(null, "", "");
            WhatsAppService wa = new WhatsAppService(new WhatsAppCloudProvider(p, RestClient.builder(), mapper),
                    new WhatsAppDevProvider(p, mapper), p);
            assertThat(wa.utilizavel()).isFalse();
            assertThat(wa.status().detalhes()).doesNotContainValue("token-meta");
        }
    }

    @Nested
    @DisplayName("Telefones e mascaramento")
    class Utilitarios {
        @Test
        void normalizaCelularBrasileiroParaE164() {
            assertThat(TelefoneUtils.normalizarCelular("(11) 99999-0001")).isEqualTo("+5511999990001");
            assertThat(TelefoneUtils.normalizarCelular("011 99999-0001")).isEqualTo("+5511999990001");
            assertThat(TelefoneUtils.normalizarCelular("+55 11 99999-0001")).isEqualTo("+5511999990001");
            assertThat(TelefoneUtils.normalizarCelular("+351912345678")).isEqualTo("+351912345678");
            assertThat(TelefoneUtils.normalizarCelular("  ")).isNull();
            assertThatThrownBy(() -> TelefoneUtils.normalizarCelular("(11) 3333-4444")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TelefoneUtils.normalizarCelular("123")).isInstanceOf(IllegalArgumentException.class);
            assertThat(TelefoneUtils.formatar("+5511999990001")).isEqualTo("+55 (11) 99999-0001");
        }

        @Test
        void mascaraDestinatarios() {
            assertThat(Mascaras.destinatario("fulano.silva@empresa.com")).isEqualTo("fu***@empresa.com");
            assertThat(Mascaras.destinatario("+5511999990001")).isEqualTo("+55 11 9****-0001");
        }
    }
}
