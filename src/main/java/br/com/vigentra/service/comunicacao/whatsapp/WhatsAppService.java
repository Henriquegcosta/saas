package br.com.vigentra.service.comunicacao.whatsapp;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.service.comunicacao.CanalMensageria;
import br.com.vigentra.service.comunicacao.EstadoIntegracao;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.service.comunicacao.StatusIntegracao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canal WhatsApp. Estados:
 *  ATIVA            - WHATSAPP_ACCESS_TOKEN e WHATSAPP_PHONE_NUMBER_ID definidos (envio real pela Meta);
 *  MODO_DEV         - WHATSAPP_DEV_MODE=true sem credenciais (gera em disco, não envia);
 *  NAO_CONFIGURADA  - nenhum dos dois: a rotina NÃO usa o canal (nada é registrado como enviado).
 */
@Service
public class WhatsAppService implements CanalMensageria {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final WhatsAppCloudProvider cloud;
    private final WhatsAppDevProvider dev;
    private final AppProperties.Whatsapp cfg;

    public WhatsAppService(WhatsAppCloudProvider cloud, WhatsAppDevProvider dev, AppProperties props) {
        this.cloud = cloud;
        this.dev = dev;
        this.cfg = props.whatsapp();
        log.info("Canal WhatsApp: {}", estado().getLabel());
    }

    @Override public CanalEnvio canal() { return CanalEnvio.WHATSAPP; }

    public EstadoIntegracao estado() {
        if (cloud.configurado()) return EstadoIntegracao.ATIVA;
        if (cfg.devMode()) return EstadoIntegracao.MODO_DEV;
        return EstadoIntegracao.NAO_CONFIGURADA;
    }

    @Override
    public StatusIntegracao status() {
        EstadoIntegracao e = estado();
        Map<String, String> d = new LinkedHashMap<>();
        d.put("API", "WhatsApp Business Cloud API (Meta) " + cfg.apiVersion());
        if (cloud.configurado()) d.put("Phone Number ID", mascarar(cfg.phoneNumberId()));
        d.put("Template - vencimento próximo", cfg.templateProximo() + " (" + cfg.idioma() + ")");
        d.put("Template - documento vencido", cfg.templateVencido() + " (" + cfg.idioma() + ")");
        d.put("Template - teste", cfg.templateTeste() + " (" + cfg.templateTesteIdioma() + ")");
        d.put("Webhook de status", cfg.appSecret() != null && !cfg.appSecret().isBlank()
                ? "configurado (assinatura verificada)" : "não configurado");
        if (e == EstadoIntegracao.MODO_DEV) d.put("Arquivos gerados em", dev.getDiretorio());
        String desc = switch (e) {
            case ATIVA -> "Mensagens enviadas pela API oficial da Meta usando modelos aprovados.";
            case MODO_DEV -> "Modo desenvolvimento: as mensagens são geradas em disco, mas NÃO são enviadas.";
            default -> "Defina WHATSAPP_ACCESS_TOKEN e WHATSAPP_PHONE_NUMBER_ID para ativar. Enquanto isso, o canal não é usado.";
        };
        return new StatusIntegracao(canal(), "Meta Cloud API", e, desc, d);
    }

    public ResultadoEnvio enviar(MensagemWhatsApp m) {
        return switch (estado()) {
            case ATIVA -> cloud.enviar(m);
            case MODO_DEV -> dev.enviar(m);
            default -> ResultadoEnvio.falha("whatsapp", "Canal WhatsApp não configurado.");
        };
    }

    /** Monta a mensagem de alerta de vencimento com o template adequado ao tipo. */
    public MensagemWhatsApp alerta(String para, TipoAlerta tipo, String nome, String documento, String vencimento,
                                   String prazo, String empresa) {
        String template = tipo == TipoAlerta.VENCIDO ? cfg.templateVencido() : cfg.templateProximo();
        List<String> p = List.of(nome, documento, vencimento, prazo, empresa);
        String previa = tipo == TipoAlerta.VENCIDO
                ? "Olá, " + nome + ". O documento \"" + documento + "\" da empresa " + empresa + " está VENCIDO desde "
                  + vencimento + " (" + prazo + "). Providencie a renovação e atualize o VIGENTRA."
                : "Olá, " + nome + ". O documento \"" + documento + "\" da empresa " + empresa + " vence em "
                  + vencimento + " (" + prazo + "). Acesse o VIGENTRA para acompanhar a renovação.";
        return new MensagemWhatsApp(para, template, cfg.idioma(), p, previa);
    }

    public MensagemWhatsApp teste(String para) {
        return new MensagemWhatsApp(para, cfg.templateTeste(), cfg.templateTesteIdioma(), List.of(),
                "Mensagem de teste do VIGENTRA (template " + cfg.templateTeste() + ").");
    }

    private static String mascarar(String id) {
        return id.length() <= 4 ? "****" : "****" + id.substring(id.length() - 4);
    }
}
