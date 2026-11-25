package br.com.vigentra.service.comunicacao;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.model.Convite;
import br.com.vigentra.domain.model.Usuario;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Monta os e-mails da jornada do usuário (confirmação, boas-vindas, convite, recuperação,
 * aviso de senha alterada, troca de e-mail) e os publica como eventos. O envio efetivo
 * acontece depois do commit, em {@link EnvioContaListener}.
 */
@Service
public class ComunicacaoContaService {

    private static final DateTimeFormatter BR_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final ApplicationEventPublisher eventos;
    private final AppProperties props;
    private final Clock clock;

    public ComunicacaoContaService(ApplicationEventPublisher eventos, AppProperties props, Clock clock) {
        this.eventos = eventos;
        this.props = props;
        this.clock = clock;
    }

    public void confirmacaoEmail(Usuario u, String token) {
        publicar(TipoMensagem.CONFIRMACAO_EMAIL, u.getEmpresa().getId(), u.getEmail(), u.getNome(),
                "[VIGENTRA] Confirme seu endereço de e-mail", "confirmacao-email", Map.of(
                        "nome", u.getNome(), "empresaNome", u.getEmpresa().getNome(),
                        "link", props.baseUrl() + "/confirmar-email?token=" + token, "horas", 48));
    }

    public void alteracaoEmail(Usuario u, String novoEmail, String token) {
        publicar(TipoMensagem.ALTERACAO_EMAIL, u.getEmpresa().getId(), novoEmail, u.getNome(),
                "[VIGENTRA] Confirme seu novo endereço de e-mail", "alteracao-email", Map.of(
                        "nome", u.getNome(), "emailAtual", u.getEmail(), "novoEmail", novoEmail,
                        "link", props.baseUrl() + "/confirmar-email/alteracao?token=" + token, "horas", 24));
    }

    public void boasVindas(Usuario u) {
        publicar(TipoMensagem.BOAS_VINDAS, u.getEmpresa().getId(), u.getEmail(), u.getNome(),
                "Boas-vindas ao VIGENTRA, " + primeiroNome(u.getNome()) + "!", "boas-vindas", Map.of(
                        "nome", primeiroNome(u.getNome()), "empresaNome", u.getEmpresa().getNome(),
                        "perfil", u.getPerfil().getNome(),
                        "linkPainel", props.baseUrl() + "/app/dashboard",
                        "linkDocumento", props.baseUrl() + "/app/documentos/novo",
                        "linkResponsaveis", props.baseUrl() + "/app/responsaveis",
                        "linkPreferencias", props.baseUrl() + "/app/configuracoes?aba=notificacoes"));
    }

    public void convite(Convite c, String token) {
        String convidante = c.getConvidadoPor() != null ? c.getConvidadoPor().getNome() : "O administrador";
        publicar(TipoMensagem.CONVITE, c.getEmpresa().getId(), c.getEmail(), c.getNome(),
                "[VIGENTRA] " + convidante + " convidou você para " + c.getEmpresa().getNome(), "convite", Map.of(
                        "nome", c.getNome() == null ? "" : primeiroNome(c.getNome()), "convidante", convidante,
                        "empresaNome", c.getEmpresa().getNome(), "perfil", c.getPerfil().getNome(),
                        "perfilDescricao", c.getPerfil().getDescricao() == null ? "" : c.getPerfil().getDescricao(),
                        "link", props.baseUrl() + "/convite?token=" + token,
                        "validade", c.getExpiraEm().format(BR_HORA)));
    }

    public void recuperacaoSenha(Usuario u, String token, int minutos) {
        publicar(TipoMensagem.RECUPERACAO_SENHA, u.getEmpresa().getId(), u.getEmail(), u.getNome(),
                "[VIGENTRA] Redefinição de senha", "recuperacao-senha", Map.of(
                        "nome", u.getNome(), "link", props.baseUrl() + "/redefinir-senha?token=" + token, "minutos", minutos));
    }

    public void senhaAlterada(Usuario u, String ip) {
        publicar(TipoMensagem.SENHA_ALTERADA, u.getEmpresa().getId(), u.getEmail(), u.getNome(),
                "[VIGENTRA] Sua senha foi alterada", "senha-alterada", Map.of(
                        "nome", u.getNome(), "quando", LocalDateTime.now(clock).format(BR_HORA),
                        "ip", ip == null ? "não identificado" : ip,
                        "linkRecuperar", props.baseUrl() + "/recuperar-senha"));
    }

    private void publicar(TipoMensagem tipo, Long empresaId, String para, String nome, String assunto, String template,
                          Map<String, Object> vars) {
        Map<String, Object> v = new HashMap<>(vars);
        v.putIfAbsent("preheader", assunto);
        eventos.publishEvent(new MensagemContaEvent(tipo, empresaId, para, nome, assunto, template, v));
    }

    private static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "" : nome.trim().split("\\s+")[0];
    }
}
