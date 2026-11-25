package br.com.vigentra.service.alerta;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.model.Configuracao;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Empresa;
import br.com.vigentra.domain.model.HistoricoEnvio;
import br.com.vigentra.domain.model.Notificacao;
import br.com.vigentra.domain.model.Perfil;
import br.com.vigentra.domain.model.PreferenciasComunicacao;
import br.com.vigentra.domain.model.RegraCanalAlerta;
import br.com.vigentra.domain.model.Responsavel;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.DocumentoRepository;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.repository.NotificacaoRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.EmailTemplateService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.PermissaoService;
import br.com.vigentra.service.RecursoNaoEncontradoException;
import br.com.vigentra.service.StatusCalculator;
import br.com.vigentra.service.comunicacao.HistoricoComunicacaoService;
import br.com.vigentra.service.comunicacao.LinkPreferenciasService;
import br.com.vigentra.service.comunicacao.LinkPreferenciasService.Alvo;
import br.com.vigentra.service.comunicacao.RegraCanalService;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.comunicacao.email.MensagemEmail;
import br.com.vigentra.service.comunicacao.sms.SmsService;
import br.com.vigentra.service.comunicacao.whatsapp.WhatsAppService;
import br.com.vigentra.util.TelefoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ROTINA AUTOMÁTICA DE ALERTAS DE VENCIMENTO (multicanal).
 *
 * <ol>
 *   <li>Consulta os documentos da empresa no banco;</li>
 *   <li>identifica os que estão num período de alerta ({@link PeriodoAlerta});</li>
 *   <li>localiza responsáveis, gestores e cópias - respeitando as preferências de cada um;</li>
 *   <li>escolhe os canais conforme a regra da empresa para aquele prazo (e-mail, WhatsApp, SMS)
 *       e a disponibilidade da integração;</li>
 *   <li>prepara o conteúdo (modelo HTML ou template aprovado do WhatsApp);</li>
 *   <li>envia pelo provedor do canal;</li>
 *   <li>registra cada mensagem no histórico;</li>
 *   <li>impede duplicidade por (documento, período, vencimento, CANAL, destinatário) - consulta + UNIQUE;</li>
 *   <li>registra falhas, agenda nova tentativa e avisa os administradores se a falha for definitiva.</li>
 * </ol>
 * Os métodos NÃO são transacionais de propósito: cada envio é persistido isoladamente.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Pessoa que pode receber o alerta, com os contatos e preferências que valem para ela. */
    record Destinatario(String nome, String email, String celular, PreferenciasComunicacao prefs,
                        Alvo alvo, Long alvoId, boolean copia) {
        String contato(CanalEnvio canal) {
            return canal == CanalEnvio.EMAIL ? email : celular;
        }
    }

    private final DocumentoRepository documentoRepo;
    private final EmpresaRepository empresaRepo;
    private final UsuarioRepository usuarioRepo;
    private final HistoricoEnvioRepository historicoRepo;
    private final NotificacaoRepository notificacaoRepo;
    private final ConfiguracaoService configuracaoService;
    private final PermissaoService permissaoService;
    private final RegraCanalService regraCanalService;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final SmsService smsService;
    private final EmailTemplateService templates;
    private final LinkPreferenciasService links;
    private final HistoricoComunicacaoService historicoComunicacao;
    private final AuditoriaService auditoria;
    private final AppProperties props;
    private final Clock clock;

    /** Evita duas execuções simultâneas para a mesma empresa (ex.: agendada + manual) na mesma instância. */
    private final Set<Long> emExecucao = ConcurrentHashMap.newKeySet();

    public AlertaService(DocumentoRepository documentoRepo, EmpresaRepository empresaRepo, UsuarioRepository usuarioRepo,
                         HistoricoEnvioRepository historicoRepo, NotificacaoRepository notificacaoRepo,
                         ConfiguracaoService configuracaoService, PermissaoService permissaoService,
                         RegraCanalService regraCanalService, EmailService emailService, WhatsAppService whatsAppService,
                         SmsService smsService, EmailTemplateService templates, LinkPreferenciasService links,
                         HistoricoComunicacaoService historicoComunicacao, AuditoriaService auditoria,
                         AppProperties props, Clock clock) {
        this.documentoRepo = documentoRepo;
        this.empresaRepo = empresaRepo;
        this.usuarioRepo = usuarioRepo;
        this.historicoRepo = historicoRepo;
        this.notificacaoRepo = notificacaoRepo;
        this.configuracaoService = configuracaoService;
        this.permissaoService = permissaoService;
        this.regraCanalService = regraCanalService;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.smsService = smsService;
        this.templates = templates;
        this.links = links;
        this.historicoComunicacao = historicoComunicacao;
        this.auditoria = auditoria;
        this.props = props;
        this.clock = clock;
    }

    // ================================================================== execução

    /** Executa para todas as empresas ativas (chamado pelo agendador diário). */
    public ResultadoRotina executarTodas() {
        ResultadoRotina total = ResultadoRotina.vazio();
        for (Empresa e : empresaRepo.findByAtivoTrue()) {
            try {
                ResultadoRotina r = executarEmpresa(e.getId(), LocalDate.now(clock));
                auditoria.registrarPara(e.getId(), null, "Sistema (rotina automática)", null, AcaoLog.ROTINA_ALERTAS,
                        "Empresa", e.getId(), "Verificação diária: " + r.resumo(), r.falhas() == 0);
                total = total.somar(r);
            } catch (RuntimeException ex) {
                log.error("Erro na rotina de alertas da empresa {}", e.getId(), ex);
            }
        }
        log.info("Rotina de alertas concluída: {}", total.resumo());
        return total;
    }

    /** Execução manual pelo usuário logado (botão "Executar verificação agora"). */
    public ResultadoRotina executarManual() {
        SecurityUtils.exigir(PermissaoCodigo.NOTIF_MANAGE);
        Long empresaId = SecurityUtils.empresaId();
        ResultadoRotina r = executarEmpresa(empresaId, LocalDate.now(clock));
        auditoria.registrar(AcaoLog.ROTINA_ALERTAS, "Empresa", empresaId, "Verificação manual: " + r.resumo());
        return r;
    }

    public ResultadoRotina executarEmpresa(Long empresaId, LocalDate hoje) {
        if (!emExecucao.add(empresaId)) {
            throw new NegocioException("A verificação de alertas já está em execução para esta empresa.");
        }
        try {
            return processar(empresaId, hoje);
        } finally {
            emExecucao.remove(empresaId);
        }
    }

    /** Canais que a rotina pode usar agora (integração ativa ou em modo desenvolvimento). */
    public Set<CanalEnvio> canaisUtilizaveis() {
        Set<CanalEnvio> s = EnumSet.noneOf(CanalEnvio.class);
        if (emailService.utilizavel()) s.add(CanalEnvio.EMAIL);
        if (whatsAppService.utilizavel()) s.add(CanalEnvio.WHATSAPP);
        if (smsService.utilizavel()) s.add(CanalEnvio.SMS);
        return s;
    }

    private ResultadoRotina processar(Long empresaId, LocalDate hoje) {
        Empresa empresa = empresaRepo.findById(empresaId).orElseThrow();
        Configuracao cfg = configuracaoService.daEmpresa(empresaId);
        Map<String, RegraCanalAlerta> regras = regraCanalService.mapa(empresaId);
        Set<CanalEnvio> disponiveis = canaisUtilizaveis();
        int maiorLimiar = cfg.getDiasAlertaLista().stream().max(Integer::compare).orElse(0);

        // 1) consulta: documentos com vencimento até hoje + maior antecedência configurada
        List<Documento> documentos = documentoRepo.findAlertaveis(empresaId, hoje.plusDays(maiorLimiar));
        List<Usuario> usuarios = usuarioRepo.findAtivos(empresaId);
        Map<Long, Set<PermissaoCodigo>> permissoesPorPerfil = new HashMap<>();

        Contadores c = new Contadores();
        for (Documento d : documentos) {
            long dias = StatusCalculator.diasParaVencer(d, hoje);
            // 2) período de alerta
            Optional<PeriodoAlerta> periodo = PeriodoAlerta.calcular(dias, cfg);
            if (periodo.isEmpty()) continue;
            c.alertas++;
            PeriodoAlerta p = periodo.get();

            c.notificacoes += notificarNoSistema(empresa, d, p, usuarios, permissoesPorPerfil);

            // 3) destinatários e 4) canais
            RegraCanalAlerta regra = regras.get(RegraCanalService.chave(p));
            List<Destinatario> destinatarios = destinatarios(d, cfg, usuarios);
            for (CanalEnvio canal : CanalEnvio.values()) {
                if (regra == null || !regra.usa(canal) || !disponiveis.contains(canal)) continue;
                Set<String> jaIncluidos = new HashSet<>();
                for (Destinatario dest : destinatarios) {
                    if (!aceita(dest, canal, p.tipo())) continue;
                    String contato = dest.contato(canal).toLowerCase();
                    if (!jaIncluidos.add(contato)) continue;
                    StatusEnvio st = reservarEEnviar(empresa, cfg, d, p, dias, canal, dest, contato);
                    c.contar(st, canal);
                }
            }
        }
        return new ResultadoRotina(documentos.size(), c.alertas, c.enviados, c.dev, c.falhas, c.duplicados,
                c.notificacoes, c.whatsapp, c.ignorados);
    }

    private static final class Contadores {
        int alertas, enviados, dev, falhas, duplicados, notificacoes, whatsapp, ignorados;

        void contar(StatusEnvio st, CanalEnvio canal) {
            if (st == null) { duplicados++; return; }
            if (canal == CanalEnvio.WHATSAPP) whatsapp++;
            switch (st) {
                case ENVIADO, ENTREGUE, LIDO -> enviados++;
                case MODO_DEV -> dev++;
                case IGNORADO -> ignorados++;
                default -> falhas++;
            }
        }
    }

    /** O destinatário aceita este canal e este tipo de alerta? (cópias da empresa só recebem e-mail) */
    private boolean aceita(Destinatario d, CanalEnvio canal, TipoAlerta tipo) {
        if (d.contato(canal) == null) return false;
        if (d.copia()) return canal == CanalEnvio.EMAIL;
        return d.prefs().aceita(canal) && d.prefs().aceita(tipo);
    }

    /** @return status do envio ou null se era duplicado */
    private StatusEnvio reservarEEnviar(Empresa empresa, Configuracao cfg, Documento d, PeriodoAlerta p, long dias,
                                        CanalEnvio canal, Destinatario dest, String contato) {
        // 7) deduplicação
        if (historicoRepo.jaRegistrado(d.getId(), p.codigo(), d.getDataVencimento(), canal, contato)) return null;
        HistoricoEnvio h = new HistoricoEnvio();
        h.setEmpresa(empresa);
        h.setDocumento(d);
        h.setDocumentoNome(d.getNome());
        h.setCanal(canal);
        h.setTipoMensagem(TipoMensagem.ALERTA_VENCIMENTO);
        h.setTipoAlerta(p.tipo());
        h.setPeriodo(p.codigo());
        h.setDataVencimentoRef(d.getDataVencimento());
        h.setDestinatario(contato);
        h.setAssunto(assunto(d, p, dias));
        try {
            h = historicoRepo.saveAndFlush(h);   // reserva o envio (UNIQUE no banco)
        } catch (DataIntegrityViolationException ex) {
            return null;
        }
        return enviar(h, d, p, dias, dest, empresa, cfg);
    }

    /** 5) prepara, 6) envia e 8) registra resultado / falha. */
    private StatusEnvio enviar(HistoricoEnvio h, Documento d, PeriodoAlerta p, long dias, Destinatario dest,
                               Empresa empresa, Configuracao cfg) {
        ResultadoEnvio r = switch (h.getCanal()) {
            case EMAIL -> emailService.enviar(email(h, d, p, dest, empresa, cfg));
            case WHATSAPP -> whatsAppService.enviar(whatsAppService.alerta(dest.celular(), p.tipo(), primeiroNome(dest.nome()),
                    d.getNome(), d.getDataVencimento().format(BR), StatusCalculator.descreverPrazo(d, LocalDate.now(clock)),
                    empresa.getNome()));
            case SMS -> smsService.enviar(dest.celular(), "VIGENTRA: " + h.getAssunto().replace("[VIGENTRA] ", ""));
        };

        LocalDateTime agora = LocalDateTime.now(clock);
        h.setTentativas(h.getTentativas() + 1);
        h.setStatus(r.status());
        h.setProvedor(r.provedor());
        if (r.idExterno() != null) h.setIdExterno(r.idExterno());
        String canal = h.getCanal().getLabel();
        if (r.sucesso()) {
            h.setEnviadoEm(r.status() == StatusEnvio.IGNORADO ? null : agora);
            h.setUltimoErro(r.status() == StatusEnvio.IGNORADO ? r.erro() : null);
            h.setProximaTentativa(null);
            if (r.status() != StatusEnvio.IGNORADO) {
                auditoria.registrarPara(empresa.getId(), null, "Sistema (rotina de alertas)", null, AcaoLog.NOTIFICACAO_ENVIADA,
                        "Documento", d.getId(), (r.status() == StatusEnvio.MODO_DEV ? "[MODO DESENVOLVIMENTO - não enviado] " : "")
                                + canal + ": alerta \"" + h.getPeriodoLabel() + "\" do documento \"" + d.getNome() + "\" para " + h.getDestinatario(), true);
            }
        } else {
            h.setUltimoErro(r.erro());
            boolean esgotou = h.getTentativas() >= props.mail().maxTentativas();
            h.setProximaTentativa(esgotou ? null : agora.plusMinutes(props.mail().intervaloRetentativaMinutos()));
            auditoria.registrarPara(empresa.getId(), null, "Sistema (rotina de alertas)", null, AcaoLog.EMAIL_FALHA,
                    "Documento", d.getId(), "Falha no envio por " + canal + " do alerta \"" + h.getPeriodoLabel() + "\" do documento \""
                            + d.getNome() + "\" para " + h.getDestinatario() + " (tentativa " + h.getTentativas() + "): " + r.erro(), false);
            if (esgotou && !h.isFalhaNotificada()) {
                h.setFalhaNotificada(true);
                avisarAdministradores(empresa, h);
            }
        }
        historicoRepo.save(h);
        return r.status();
    }

    private MensagemEmail email(HistoricoEnvio h, Documento d, PeriodoAlerta p, Destinatario dest, Empresa empresa, Configuracao cfg) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("empresaNome", empresa.getNome());
        v.put("destinatarioNome", dest.nome());
        v.put("documentoNome", d.getNome());
        v.put("documentoId", d.getId());
        v.put("categoria", d.getCategoria().getNome());
        v.put("numero", d.getNumero());
        v.put("empresaResponsavel", d.getEmpresaResponsavel());
        v.put("responsavelNome", d.getResponsavel() != null ? d.getResponsavel().getNome() : null);
        v.put("vencimento", d.getDataVencimento().format(BR));
        v.put("prazo", StatusCalculator.descreverPrazo(d, LocalDate.now(clock)));
        v.put("tipo", p.tipo().name());
        v.put("periodoLabel", h.getPeriodoLabel());
        v.put("assinatura", cfg.getEmailAssinatura());
        v.put("preheader", h.getAssunto());
        String descadastro = null;
        if (dest.alvo() != null) {
            v.put("linkPreferencias", links.linkPreferencias(dest.alvo(), dest.alvoId(), dest.email()));
            descadastro = links.linkDescadastro(dest.alvo(), dest.alvoId(), dest.email());
            v.put("linkDescadastro", descadastro);
        }
        String template = p.tipo() == TipoAlerta.VENCIDO ? "alerta-vencido" : "alerta-proximo";
        return new MensagemEmail(h.getDestinatario(), dest.nome(), cfg.getEmailRemetenteNome(), cfg.getEmailResposta(),
                h.getAssunto(), templates.render(template, v), descadastro, "alerta");
    }

    /**
     * Responsável do documento (ou o usuário vinculado a ele, que gerencia as próprias preferências),
     * administradores/gestores e e-mails em cópia configurados pela empresa.
     */
    List<Destinatario> destinatarios(Documento d, Configuracao cfg, List<Usuario> usuarios) {
        List<Destinatario> lista = new ArrayList<>();
        Set<Long> usuariosIncluidos = new HashSet<>();
        Responsavel resp = d.getResponsavel();
        if (cfg.isNotificarResponsavel() && resp != null && resp.isAtivo()) {
            Usuario vinculado = resp.getUsuario();
            if (vinculado != null) {
                if (vinculado.isAtivo()) {
                    lista.add(deUsuario(vinculado));
                    usuariosIncluidos.add(vinculado.getId());
                }
            } else {
                lista.add(new Destinatario(resp.getNome(), resp.getEmail().toLowerCase(), resp.getCelular(),
                        resp.getPreferencias(), Alvo.R, resp.getId(), false));
            }
        }
        if (cfg.isNotificarGestores()) {
            for (Usuario u : usuarios) {
                PerfilCodigo pc = u.getPerfil().getCodigo();
                if ((pc == PerfilCodigo.ADMIN || pc == PerfilCodigo.GESTOR) && usuariosIncluidos.add(u.getId())) {
                    lista.add(deUsuario(u));
                }
            }
        }
        for (String cc : cfg.getEmailCopiaLista()) {
            lista.add(new Destinatario(cc, cc.toLowerCase(), null, null, null, null, true));
        }
        return lista;
    }

    private static Destinatario deUsuario(Usuario u) {
        return new Destinatario(u.getNome(), u.getEmail().toLowerCase(), u.getCelular(), u.getPreferencias(), Alvo.U, u.getId(), false);
    }

    private int notificarNoSistema(Empresa empresa, Documento d, PeriodoAlerta p, List<Usuario> usuarios,
                                   Map<Long, Set<PermissaoCodigo>> cache) {
        String chave = "doc:" + d.getId() + ":" + p.codigo() + ":" + d.getDataVencimento();
        Long responsavelUsuarioId = d.getResponsavel() != null && d.getResponsavel().getUsuario() != null
                ? d.getResponsavel().getUsuario().getId() : null;
        Long criadorId = d.getCriadoPor() != null ? d.getCriadoPor().getId() : null;
        int criadas = 0;
        for (Usuario u : usuarios) {
            if (!u.isNotificacoesApp()) continue;
            Perfil perfil = u.getPerfil();
            Set<PermissaoCodigo> perms = cache.computeIfAbsent(perfil.getId(),
                    k -> permissaoService.permissoesDe(empresa.getId(), perfil));
            boolean acesso = perms.contains(PermissaoCodigo.DOC_VIEW_ALL)
                    || u.getId().equals(responsavelUsuarioId) || u.getId().equals(criadorId);
            if (!acesso || notificacaoRepo.existsByUsuarioIdAndChave(u.getId(), chave)) continue;
            Notificacao n = new Notificacao();
            n.setEmpresa(empresa);
            n.setUsuario(u);
            n.setDocumento(d);
            n.setTipo(p.tipo());
            n.setChave(chave);
            n.setTitulo(tituloNotificacao(d, p));
            n.setMensagem(StatusCalculator.descreverPrazo(d, LocalDate.now(clock)) + " (" + d.getDataVencimento().format(BR)
                    + ") · " + d.getCategoria().getNome()
                    + (d.getResponsavel() != null ? " · Responsável: " + d.getResponsavel().getNome() : ""));
            try {
                notificacaoRepo.save(n);
                criadas++;
            } catch (DataIntegrityViolationException ignorada) {
                // já criada por execução concorrente
            }
        }
        return criadas;
    }

    // ================================================================== falha definitiva -> aviso administrativo

    /** Avisa os administradores (por e-mail) que um alerta não pôde ser entregue após todas as tentativas. */
    private void avisarAdministradores(Empresa empresa, HistoricoEnvio falha) {
        for (Usuario admin : usuarioRepo.findAtivosPorPerfil(empresa.getId(), PerfilCodigo.ADMIN)) {
            if (!admin.getPreferencias().isReceberEmail()) continue;
            String assunto = "[VIGENTRA] Falha definitiva no envio de um alerta";
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("nome", primeiroNome(admin.getNome()));
            v.put("empresaNome", empresa.getNome());
            v.put("documentoNome", falha.getDocumentoNome());
            v.put("canal", falha.getCanal().getLabel());
            v.put("destinatario", falha.getDestinatario());
            v.put("periodoLabel", falha.getPeriodoLabel());
            v.put("tentativas", falha.getTentativas());
            v.put("erro", falha.getUltimoErro());
            v.put("linkHistorico", props.baseUrl() + "/app/notificacoes/envios?status=FALHA");
            v.put("preheader", "Um alerta de vencimento não foi entregue");
            ResultadoEnvio r = emailService.enviar(new MensagemEmail(admin.getEmail(), admin.getNome(), "VIGENTRA", null,
                    assunto, templates.render("alerta-admin", v), null, "admin"));
            historicoComunicacao.registrar(empresa.getId(), CanalEnvio.EMAIL, TipoMensagem.ALERTA_ADMINISTRATIVO,
                    "Falha: " + falha.getDocumentoNome(), admin.getEmail(), assunto, r);
        }
    }

    // ================================================================== retentativas

    /** Reprocessa envios com falha cujo horário de nova tentativa chegou (agendado de hora em hora). */
    public int reprocessarFalhas() {
        int ok = 0;
        for (HistoricoEnvio h : historicoRepo.findParaRetentativa(props.mail().maxTentativas(), LocalDateTime.now(clock))) {
            if (reenviar(h) != StatusEnvio.FALHA) ok++;
        }
        return ok;
    }

    /** Reenvio manual de um registro com falha (Notificações > Histórico de envios). */
    public StatusEnvio reenviarManual(Long historicoId) {
        SecurityUtils.exigir(PermissaoCodigo.NOTIF_MANAGE);
        HistoricoEnvio h = historicoRepo.findByIdAndEmpresaId(historicoId, SecurityUtils.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de envio não encontrado."));
        if (h.getStatus() != StatusEnvio.FALHA) throw new NegocioException("Somente envios com falha podem ser reenviados.");
        if (!h.getTipoMensagem().isRetentavel()) {
            throw new NegocioException("Mensagens de conta (confirmação, convite, senha) contêm links de uso único: "
                    + "solicite um novo envio pela tela correspondente.");
        }
        StatusEnvio st = reenviar(h);
        auditoria.registrar(AcaoLog.NOTIFICACAO_ENVIADA, "HistoricoEnvio", h.getId(),
                "Reenvio manual (" + h.getCanal().getLabel() + ") do alerta \"" + h.getDocumentoNome() + "\" para "
                        + h.getDestinatario() + ": " + st.getLabel());
        return st;
    }

    private StatusEnvio reenviar(HistoricoEnvio h) {
        Long empresaId = h.getEmpresa().getId();
        Documento d = h.getDocumento() == null ? null : documentoRepo.findParaAlerta(h.getDocumento().getId(), empresaId).orElse(null);
        if (d == null || d.getDataVencimento() == null || !d.getDataVencimento().equals(h.getDataVencimentoRef())) {
            return cancelarRetentativa(h, "Reenvio cancelado: documento excluído ou com vencimento alterado.");
        }
        Configuracao cfg = configuracaoService.daEmpresa(empresaId);
        PeriodoAlerta p = new PeriodoAlerta(h.getPeriodo(), h.getTipoAlerta(), 0);
        // as preferências valem no momento do reenvio: se a pessoa desistiu do canal, não reenviamos
        Optional<Destinatario> dest = destinatarios(d, cfg, usuarioRepo.findAtivos(empresaId)).stream()
                .filter(x -> h.getDestinatario().equalsIgnoreCase(x.contato(h.getCanal())))
                .filter(x -> aceita(x, h.getCanal(), h.getTipoAlerta()))
                .findFirst();
        if (dest.isEmpty()) {
            return cancelarRetentativa(h, "Reenvio cancelado: o destinatário não deve mais receber este alerta por "
                    + h.getCanal().getLabel() + " (preferência ou cadastro alterado).");
        }
        if (!canaisUtilizaveis().contains(h.getCanal())) {
            return cancelarRetentativa(h, "Reenvio cancelado: canal " + h.getCanal().getLabel() + " não está disponível.");
        }
        long dias = StatusCalculator.diasParaVencer(d, LocalDate.now(clock));
        return enviar(h, d, p, dias, dest.get(), empresaRepo.findById(empresaId).orElseThrow(), cfg);
    }

    private StatusEnvio cancelarRetentativa(HistoricoEnvio h, String motivo) {
        h.setProximaTentativa(null);
        h.setTentativas(Math.max(h.getTentativas(), props.mail().maxTentativas()));
        h.setUltimoErro(motivo);
        historicoRepo.save(h);
        return StatusEnvio.FALHA;
    }

    // ================================================================== textos

    private String assunto(Documento d, PeriodoAlerta p, long dias) {
        String venc = d.getDataVencimento().format(BR);
        return switch (p.tipo()) {
            case VENCE_HOJE -> "[VIGENTRA] VENCE HOJE: " + d.getNome() + " (" + venc + ")";
            case VENCIDO -> "[VIGENTRA] VENCIDO há " + (-dias) + " dia(s): " + d.getNome() + " (" + venc + ")";
            default -> "[VIGENTRA] " + d.getNome() + " vence em " + dias + " dia(s) (" + venc + ")";
        };
    }

    private String tituloNotificacao(Documento d, PeriodoAlerta p) {
        return switch (p.tipo()) {
            case VENCE_HOJE -> d.getNome() + " vence hoje";
            case VENCIDO -> d.getNome() + " está vencido";
            default -> d.getNome() + " vence em breve";
        };
    }

    static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "" : nome.trim().split("\\s+")[0];
    }

    /** Usado pelos painéis: formata o destinatário de WhatsApp para exibição. */
    public static String exibirContato(CanalEnvio canal, String contato) {
        return canal == CanalEnvio.EMAIL ? contato : TelefoneUtils.formatar(contato);
    }
}
