package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.model.PreferenciasComunicacao;
import br.com.vigentra.domain.model.Responsavel;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.RecursoNaoEncontradoException;
import br.com.vigentra.service.comunicacao.LinkPreferenciasService.Alvo;
import br.com.vigentra.util.RequestUtils;
import br.com.vigentra.util.TelefoneUtils;
import br.com.vigentra.web.dto.PreferenciasForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Preferências de comunicação e consentimento (LGPD e política de opt-in da Meta).
 *
 * Regras:
 * - WhatsApp/SMS exigem celular válido E consentimento marcado explicitamente;
 * - o consentimento guarda data e ORIGEM (quem registrou, por qual meio, de qual IP);
 * - trocar o celular revoga os consentimentos anteriores (eles valem para um número);
 * - responsável vinculado a um usuário: as preferências são do usuário (ele mesmo decide).
 */
@Service
public class PreferenciasService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UsuarioRepository usuarioRepo;
    private final ResponsavelRepository responsavelRepo;
    private final LinkPreferenciasService links;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public PreferenciasService(UsuarioRepository usuarioRepo, ResponsavelRepository responsavelRepo,
                               LinkPreferenciasService links, AuditoriaService auditoria, Clock clock) {
        this.usuarioRepo = usuarioRepo;
        this.responsavelRepo = responsavelRepo;
        this.links = links;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ próprio usuário (painel)

    @Transactional
    public void salvarMinhas(PreferenciasForm f) {
        UsuarioPrincipal p = SecurityUtils.usuario();
        Usuario u = usuarioRepo.findById(p.getId()).orElseThrow();
        String celular = aplicar(u.getPreferencias(), u.getCelular(), f,
                "Próprio usuário pelo painel (IP " + ip() + ")");
        u.setCelular(celular);
        u.setNotificacoesApp(f.isNotificacoesApp());
        usuarioRepo.save(u);
        auditoria.registrar(AcaoLog.PREFERENCIAS_ALTERADAS, "Usuario", u.getId(), resumo(u.getPreferencias(), celular));
    }

    // ------------------------------------------------------------------ gestor registrando para um responsável

    /** Aplica as preferências informadas pelo gestor (o consentimento fica registrado em nome dele). */
    public void aplicarPeloGestor(Responsavel r, PreferenciasForm f) {
        if (r.getUsuario() != null) return; // o usuário vinculado gerencia as próprias preferências
        UsuarioPrincipal gestor = SecurityUtils.usuario();
        String celular = aplicar(r.getPreferencias(), r.getCelular(), f,
                "Registrado por " + gestor.getNome() + " <" + gestor.getEmail() + ">, que declarou ter obtido a autorização do titular");
        r.setCelular(celular);
    }

    // ------------------------------------------------------------------ link público (sem login, assinado)

    public record Titular(Alvo alvo, Long id, String nome, String email, String empresa, String celular,
                          PreferenciasComunicacao preferencias) {}

    @Transactional(readOnly = true)
    public Titular titular(Alvo alvo, Long id, String assinatura) {
        if (alvo == Alvo.U) {
            Usuario u = usuarioRepo.findById(id == null ? -1 : id)
                    .filter(x -> links.valido(alvo, x.getId(), x.getEmail(), assinatura))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Link inválido."));
            return new Titular(alvo, u.getId(), u.getNome(), u.getEmail(), u.getEmpresa().getNome(), u.getCelular(), u.getPreferencias());
        }
        Responsavel r = responsavelRepo.findComEmpresa(id == null ? -1 : id)
                .filter(x -> links.valido(Alvo.R, x.getId(), x.getEmail(), assinatura))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link inválido."));
        if (r.getUsuario() != null) {
            Usuario u = r.getUsuario();
            return new Titular(Alvo.U, u.getId(), u.getNome(), u.getEmail(), r.getEmpresa().getNome(), u.getCelular(), u.getPreferencias());
        }
        return new Titular(Alvo.R, r.getId(), r.getNome(), r.getEmail(), r.getEmpresa().getNome(), r.getCelular(), r.getPreferencias());
    }

    @Transactional
    public void salvarPeloLink(Alvo alvo, Long id, String assinatura, PreferenciasForm f) {
        Titular t = titular(alvo, id, assinatura);
        String origem = "Titular pelo link de preferências enviado por e-mail (IP " + ip() + ")";
        if (t.alvo() == Alvo.U) {
            Usuario u = usuarioRepo.findById(t.id()).orElseThrow();
            u.setCelular(aplicar(u.getPreferencias(), u.getCelular(), f, origem));
            usuarioRepo.save(u);
            auditarTitular(u.getEmpresa().getId(), u.getNome(), u.getEmail(), "Usuario", u.getId(), resumo(u.getPreferencias(), u.getCelular()));
        } else {
            Responsavel r = responsavelRepo.findComEmpresa(t.id()).orElseThrow();
            r.setCelular(aplicar(r.getPreferencias(), r.getCelular(), f, origem));
            responsavelRepo.save(r);
            auditarTitular(r.getEmpresa().getId(), r.getNome(), r.getEmail(), "Responsavel", r.getId(), resumo(r.getPreferencias(), r.getCelular()));
        }
    }

    /** Descadastro de todos os alertas por e-mail (link do rodapé ou List-Unsubscribe One-Click). */
    @Transactional
    public Titular descadastrarEmail(Alvo alvo, Long id, String assinatura) {
        Titular t = titular(alvo, id, assinatura);
        if (t.alvo() == Alvo.U) {
            Usuario u = usuarioRepo.findById(t.id()).orElseThrow();
            u.getPreferencias().setReceberEmail(false);
            usuarioRepo.save(u);
            auditarTitular(u.getEmpresa().getId(), u.getNome(), u.getEmail(), "Usuario", u.getId(),
                    "Descadastro dos alertas por e-mail pelo link da mensagem");
        } else {
            Responsavel r = responsavelRepo.findComEmpresa(t.id()).orElseThrow();
            r.getPreferencias().setReceberEmail(false);
            responsavelRepo.save(r);
            auditarTitular(r.getEmpresa().getId(), r.getNome(), r.getEmail(), "Responsavel", r.getId(),
                    "Descadastro dos alertas por e-mail pelo link da mensagem");
        }
        return t;
    }

    /** Pedido de descadastro recebido pelo próprio WhatsApp (ex.: "SAIR"). Vale para todas as empresas. */
    @Transactional
    public int revogarWhatsappPorCelular(String e164) {
        int n = 0;
        for (Usuario u : usuarioRepo.findByCelularTodasEmpresas(e164)) {
            if (u.getPreferencias().isReceberWhatsapp()) {
                u.getPreferencias().definirWhatsapp(false, null, null);
                auditarTitular(u.getEmpresa().getId(), u.getNome(), u.getEmail(), "Usuario", u.getId(),
                        "WhatsApp: titular pediu para não receber mais mensagens (resposta pelo próprio WhatsApp)");
                n++;
            }
        }
        for (Responsavel r : responsavelRepo.findByCelularTodasEmpresas(e164)) {
            if (r.getPreferencias().isReceberWhatsapp()) {
                r.getPreferencias().definirWhatsapp(false, null, null);
                auditarTitular(r.getEmpresa().getId(), r.getNome(), r.getEmail(), "Responsavel", r.getId(),
                        "WhatsApp: titular pediu para não receber mais mensagens (resposta pelo próprio WhatsApp)");
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ regra comum

    /** @return o celular normalizado a gravar */
    String aplicar(PreferenciasComunicacao p, String celularAtual, PreferenciasForm f, String origem) {
        String celular;
        try {
            celular = TelefoneUtils.normalizarCelular(f.getCelular());
        } catch (IllegalArgumentException e) {
            throw new NegocioException(e.getMessage());
        }
        if (!Objects.equals(celular, celularAtual)) p.revogarConsentimentosDeTelefone();

        if ((f.isReceberWhatsapp() || f.isReceberSms()) && celular == null) {
            throw new NegocioException("Informe um celular para receber mensagens por WhatsApp ou SMS.");
        }
        if (f.isReceberWhatsapp() && !f.isConsentimentoWhatsapp()) {
            throw new NegocioException("Para ativar o WhatsApp, marque a autorização de recebimento de mensagens.");
        }
        if (f.isReceberSms() && !f.isConsentimentoSms()) {
            throw new NegocioException("Para ativar o SMS, marque a autorização de recebimento de mensagens.");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        p.setReceberEmail(f.isReceberEmail());
        p.definirWhatsapp(f.isReceberWhatsapp(), origem, agora);
        p.definirSms(f.isReceberSms(), origem, agora);
        p.setAlertaAntecedencia(f.isAlertaAntecedencia());
        p.setAlertaVenceHoje(f.isAlertaVenceHoje());
        p.setAlertaVencido(f.isAlertaVencido());
        return celular;
    }

    private String resumo(PreferenciasComunicacao p, String celular) {
        return "Preferências: e-mail " + sn(p.isReceberEmail()) + ", WhatsApp " + sn(p.isReceberWhatsapp())
                + (p.getWhatsappConsentimentoEm() != null ? " (consentimento em " + p.getWhatsappConsentimentoEm().format(BR) + ")" : "")
                + ", SMS " + sn(p.isReceberSms()) + (celular != null ? ", celular " + TelefoneUtils.formatar(celular) : "");
    }

    private void auditarTitular(Long empresaId, String nome, String email, String entidade, Long id, String desc) {
        auditoria.registrarPara(empresaId, null, nome + " (titular)", email,
                desc.startsWith("Descadastro") || desc.startsWith("WhatsApp:") ? AcaoLog.CONSENTIMENTO_REVOGADO : AcaoLog.PREFERENCIAS_ALTERADAS,
                entidade, id, desc, true);
    }

    private static String sn(boolean b) { return b ? "sim" : "não"; }

    private static String ip() {
        String ip = RequestUtils.ipCliente();
        return ip == null ? "desconhecido" : ip;
    }
}
