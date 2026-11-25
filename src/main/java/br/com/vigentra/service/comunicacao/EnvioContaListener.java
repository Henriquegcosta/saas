package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.EmailTemplateService;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.comunicacao.email.MensagemEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Processa as solicitações de e-mail de conta APÓS o commit da transação que as originou
 * (se o cadastro falhar, nenhum e-mail é enviado) e em segundo plano (o usuário não espera o provedor).
 */
@Component
public class EnvioContaListener {

    private static final Logger log = LoggerFactory.getLogger(EnvioContaListener.class);

    private final EmailService emailService;
    private final EmailTemplateService templates;
    private final HistoricoComunicacaoService historico;
    private final AuditoriaService auditoria;

    public EnvioContaListener(EmailService emailService, EmailTemplateService templates,
                              HistoricoComunicacaoService historico, AuditoriaService auditoria) {
        this.emailService = emailService;
        this.templates = templates;
        this.historico = historico;
        this.auditoria = auditoria;
    }

    @Async("comunicacaoExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void processar(MensagemContaEvent e) {
        try {
            String html = templates.render(e.template(), e.variaveis());
            ResultadoEnvio r = emailService.enviar(new MensagemEmail(e.para(), e.nome(), "VIGENTRA", null,
                    e.assunto(), html, null, "conta"));
            historico.registrar(e.empresaId(), CanalEnvio.EMAIL, e.tipo(), e.tipo().getLabel(), e.para(), e.assunto(), r);
            if (!r.sucesso()) {
                auditoria.registrarPara(e.empresaId(), null, "Sistema (e-mails de conta)", null, AcaoLog.EMAIL_FALHA,
                        "Usuario", null, "Falha no envio de \"" + e.tipo().getLabel() + "\" para " + e.para() + ": " + r.erro(), false);
            }
        } catch (RuntimeException ex) {
            log.error("Erro ao processar {}", e, ex);
        }
    }
}
