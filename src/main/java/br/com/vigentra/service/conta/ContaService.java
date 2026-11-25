package br.com.vigentra.service.conta;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.TipoToken;
import br.com.vigentra.domain.model.TokenUsuario;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.LimitadorTaxa;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.comunicacao.ComunicacaoContaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Jornada da conta:
 * - confirmação do endereço de e-mail (obrigatória para acessar a área logada);
 * - e-mail de boas-vindas SOMENTE após a confirmação;
 * - troca de e-mail com confirmação do novo endereço (o atual vale até a confirmação).
 */
@Service
public class ContaService {

    private final UsuarioRepository usuarioRepo;
    private final ResponsavelRepository responsavelRepo;
    private final TokenService tokens;
    private final ComunicacaoContaService comunicacao;
    private final AuditoriaService auditoria;
    private final LimitadorTaxa limitador;
    private final Clock clock;

    public ContaService(UsuarioRepository usuarioRepo, ResponsavelRepository responsavelRepo, TokenService tokens,
                        ComunicacaoContaService comunicacao, AuditoriaService auditoria, LimitadorTaxa limitador, Clock clock) {
        this.usuarioRepo = usuarioRepo;
        this.responsavelRepo = responsavelRepo;
        this.tokens = tokens;
        this.comunicacao = comunicacao;
        this.auditoria = auditoria;
        this.limitador = limitador;
        this.clock = clock;
    }

    /** Emite o token e agenda o e-mail de confirmação (enviado após o commit). */
    @Transactional
    public void solicitarConfirmacao(Usuario u) {
        if (u.isEmailVerificado()) return;
        String token = tokens.emitir(u, TipoToken.CONFIRMACAO_EMAIL, null);
        comunicacao.confirmacaoEmail(u, token);
    }

    /** Reenvio pela tela "confirme seu e-mail" (usuário logado e ainda não verificado). */
    @Transactional
    public void reenviarConfirmacao() {
        Usuario u = usuarioRepo.findById(SecurityUtils.usuario().getId()).orElseThrow();
        if (u.isEmailVerificado()) throw new NegocioException("Seu e-mail já está confirmado.");
        if (!limitador.permitir("confirmacao:" + u.getId(), 3, Duration.ofHours(1))
                || !limitador.permitir("confirmacao-1min:" + u.getId(), 1, Duration.ofMinutes(1))) {
            throw new NegocioException("Aguarde alguns minutos antes de solicitar um novo e-mail de confirmação.");
        }
        solicitarConfirmacao(u);
    }

    /** Confirma o e-mail pelo token (uso único) e dispara as boas-vindas. */
    @Transactional
    public Usuario confirmar(String token) {
        TokenUsuario t = tokens.validar(token, TipoToken.CONFIRMACAO_EMAIL)
                .orElseThrow(() -> new NegocioException("Link de confirmação inválido, expirado ou já utilizado."));
        Usuario u = t.getUsuario();
        tokens.consumir(t);
        if (!u.isEmailVerificado()) {
            u.setEmailVerificadoEm(LocalDateTime.now(clock));
            usuarioRepo.save(u);
            comunicacao.boasVindas(u);
            auditoria.registrarPara(u.getEmpresa().getId(), u.getId(), u.getNome(), u.getEmail(), AcaoLog.EMAIL_CONFIRMADO,
                    "Usuario", u.getId(), "Endereço de e-mail confirmado", true);
        }
        return u;
    }

    /** Solicita a troca do e-mail de login: envia confirmação ao NOVO endereço. */
    @Transactional
    public void solicitarAlteracaoEmail(String novoEmail) {
        Usuario u = usuarioRepo.findById(SecurityUtils.usuario().getId()).orElseThrow();
        String email = novoEmail == null ? "" : novoEmail.trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new NegocioException("Informe um e-mail válido.");
        if (email.equalsIgnoreCase(u.getEmail())) throw new NegocioException("Este já é o seu e-mail atual.");
        if (usuarioRepo.existsByEmailIgnoreCase(email)) throw new NegocioException("Este e-mail já está em uso por outra conta.");
        if (!limitador.permitir("alteracao-email:" + u.getId(), 3, Duration.ofHours(1))) {
            throw new NegocioException("Muitas solicitações. Tente novamente mais tarde.");
        }
        u.setEmailPendente(email);
        usuarioRepo.save(u);
        String token = tokens.emitir(u, TipoToken.ALTERACAO_EMAIL, null);
        comunicacao.alteracaoEmail(u, email, token);
        auditoria.registrar(AcaoLog.USUARIO_ALTERADO, "Usuario", u.getId(),
                "Solicitada a troca do e-mail de login para " + email + " (aguardando confirmação)");
    }

    @Transactional
    public Usuario confirmarAlteracaoEmail(String token) {
        TokenUsuario t = tokens.validar(token, TipoToken.ALTERACAO_EMAIL)
                .orElseThrow(() -> new NegocioException("Link inválido, expirado ou já utilizado."));
        Usuario u = t.getUsuario();
        tokens.consumir(t);
        String novo = u.getEmailPendente();
        if (novo == null) throw new NegocioException("Não há troca de e-mail pendente.");
        if (usuarioRepo.findByEmailIgnoreCase(novo).filter(o -> !o.getId().equals(u.getId())).isPresent()) {
            throw new NegocioException("Este e-mail passou a ser usado por outra conta.");
        }
        String antigo = u.getEmail();
        u.setEmail(novo);
        u.setEmailPendente(null);
        u.setEmailVerificadoEm(LocalDateTime.now(clock));
        usuarioRepo.save(u);
        responsavelRepo.findByUsuarioId(u.getId()).forEach(r -> r.setEmail(novo));
        auditoria.registrarPara(u.getEmpresa().getId(), u.getId(), u.getNome(), novo, AcaoLog.USUARIO_ALTERADO,
                "Usuario", u.getId(), "E-mail de login alterado de " + antigo + " para " + novo + " (confirmado)", true);
        return u;
    }

    @Transactional
    public void cancelarAlteracaoEmail() {
        Usuario u = usuarioRepo.findById(SecurityUtils.usuario().getId()).orElseThrow();
        u.setEmailPendente(null);
        usuarioRepo.save(u);
    }
}
