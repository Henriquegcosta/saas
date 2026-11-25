package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.model.Configuracao;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.LimitadorTaxa;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.EmailTemplateService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.comunicacao.email.MensagemEmail;
import br.com.vigentra.service.comunicacao.sms.SmsService;
import br.com.vigentra.service.comunicacao.whatsapp.WhatsAppService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Status das integrações (sem credenciais) e envio de mensagens de teste pelos painéis. */
@Service
public class CanaisService {

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final SmsService smsService;
    private final EmailTemplateService templates;
    private final HistoricoComunicacaoService historico;
    private final ConfiguracaoService configuracaoService;
    private final UsuarioRepository usuarioRepo;
    private final AuditoriaService auditoria;
    private final LimitadorTaxa limitador;

    public CanaisService(EmailService emailService, WhatsAppService whatsAppService, SmsService smsService,
                         EmailTemplateService templates, HistoricoComunicacaoService historico,
                         ConfiguracaoService configuracaoService, UsuarioRepository usuarioRepo,
                         AuditoriaService auditoria, LimitadorTaxa limitador) {
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.smsService = smsService;
        this.templates = templates;
        this.historico = historico;
        this.configuracaoService = configuracaoService;
        this.usuarioRepo = usuarioRepo;
        this.auditoria = auditoria;
        this.limitador = limitador;
    }

    public List<StatusIntegracao> status() {
        return List.of(emailService.status(), whatsAppService.status(), smsService.status());
    }

    public StatusIntegracao status(CanalEnvio canal) {
        return switch (canal) {
            case EMAIL -> emailService.status();
            case WHATSAPP -> whatsAppService.status();
            case SMS -> smsService.status();
        };
    }

    /** E-mail de teste para o próprio usuário logado. */
    public ResultadoEnvio testarEmail() {
        UsuarioPrincipal u = SecurityUtils.usuario();
        limitar(u, CanalEnvio.EMAIL);
        Configuracao cfg = configuracaoService.daEmpresa(u.getEmpresaId());
        String assunto = "[VIGENTRA] E-mail de teste - configuração de envio";
        String html = templates.render("teste", Map.of("nome", u.getPrimeiroNome(), "empresaNome", u.getEmpresaNome(),
                "assinatura", cfg.getEmailAssinatura() == null ? "" : cfg.getEmailAssinatura(),
                "provedor", emailService.status().provedor(), "preheader", "Teste de envio do VIGENTRA"));
        ResultadoEnvio r = emailService.enviar(new MensagemEmail(u.getEmail(), u.getNome(), cfg.getEmailRemetenteNome(),
                cfg.getEmailResposta(), assunto, html, null, "teste"));
        registrar(u, CanalEnvio.EMAIL, u.getEmail(), assunto, r);
        return r;
    }

    /** WhatsApp de teste (template de teste) para o celular cadastrado do próprio usuário. */
    public ResultadoEnvio testarWhatsapp() {
        UsuarioPrincipal p = SecurityUtils.usuario();
        if (!whatsAppService.utilizavel()) {
            throw new NegocioException("O WhatsApp não está configurado. Veja como ativar em docs/NOTIFICACOES.md.");
        }
        Usuario u = usuarioRepo.findById(p.getId()).orElseThrow();
        if (u.getCelular() == null) {
            throw new NegocioException("Cadastre seu celular em Configurações › Preferências de notificação para receber o teste.");
        }
        limitar(p, CanalEnvio.WHATSAPP);
        ResultadoEnvio r = whatsAppService.enviar(whatsAppService.teste(u.getCelular()));
        registrar(p, CanalEnvio.WHATSAPP, u.getCelular(), "Teste do WhatsApp (template " + whatsAppService.teste(u.getCelular()).template() + ")", r);
        return r;
    }

    private void limitar(UsuarioPrincipal u, CanalEnvio canal) {
        if (!limitador.permitir("teste:" + canal + ":" + u.getId(), 5, Duration.ofMinutes(10))) {
            throw new NegocioException("Limite de mensagens de teste atingido. Aguarde alguns minutos.");
        }
    }

    private void registrar(UsuarioPrincipal u, CanalEnvio canal, String para, String assunto, ResultadoEnvio r) {
        var h = historico.registrar(u.getEmpresaId(), canal, TipoMensagem.TESTE, "(mensagem de teste)", para, assunto, r);
        auditoria.registrar(r.sucesso() ? AcaoLog.NOTIFICACAO_ENVIADA : AcaoLog.EMAIL_FALHA, "HistoricoEnvio", h.getId(),
                "Teste de " + canal.getLabel() + " para " + para + ": " + r.status().getLabel() + (r.erro() != null ? " - " + r.erro() : ""));
    }
}
