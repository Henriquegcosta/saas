package br.com.vigentra.service;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.TipoToken;
import br.com.vigentra.domain.model.TokenUsuario;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.LimitadorTaxa;
import br.com.vigentra.security.SessaoService;
import br.com.vigentra.service.comunicacao.ComunicacaoContaService;
import br.com.vigentra.service.conta.TokenService;
import br.com.vigentra.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Recuperação segura de senha:
 * - token aleatório de 256 bits, guardado apenas como hash, válido por 30 min e de uso único;
 * - resposta idêntica exista ou não o e-mail (evita enumeração de contas);
 * - limite de solicitações por IP e por conta;
 * - ao redefinir: sessões encerradas e e-mail de aviso "sua senha foi alterada".
 */
@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private final UsuarioRepository usuarioRepo;
    private final TokenService tokens;
    private final ComunicacaoContaService comunicacao;
    private final AuditoriaService auditoria;
    private final SessaoService sessaoService;
    private final LimitadorTaxa limitador;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public RecuperacaoSenhaService(UsuarioRepository usuarioRepo, TokenService tokens, ComunicacaoContaService comunicacao,
                                   AuditoriaService auditoria, SessaoService sessaoService, LimitadorTaxa limitador,
                                   PasswordEncoder encoder, AppProperties props, Clock clock) {
        this.usuarioRepo = usuarioRepo;
        this.tokens = tokens;
        this.comunicacao = comunicacao;
        this.auditoria = auditoria;
        this.sessaoService = sessaoService;
        this.limitador = limitador;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void solicitar(String email) {
        String ip = RequestUtils.ipCliente();
        if (ip != null && !limitador.permitir("recuperacao-ip:" + ip, 5, Duration.ofMinutes(15))) {
            throw new NegocioException("Muitas solicitações. Aguarde alguns minutos e tente novamente.");
        }
        if (email == null || email.isBlank()) return;
        Optional<Usuario> opt = usuarioRepo.findByEmailComEmpresa(email.trim());
        if (opt.isEmpty() || !opt.get().isAtivo()) {
            log.info("Recuperação de senha solicitada para e-mail inexistente/inativo (resposta genérica).");
            return;
        }
        Usuario u = opt.get();
        // limite silencioso por conta: protege a caixa do usuário contra "bombardeio" de e-mails
        if (!limitador.permitir("recuperacao-conta:" + u.getId(), 3, Duration.ofHours(1))) return;
        emitir(u, "Link de redefinição de senha solicitado pelo próprio usuário");
    }

    /** Usado pelo administrador/gestor em Usuários › Editar › "Enviar redefinição de senha". */
    @Transactional
    public void enviarParaUsuario(Usuario u, String solicitante) {
        if (!limitador.permitir("recuperacao-conta:" + u.getId(), 3, Duration.ofHours(1))) {
            throw new NegocioException("Um link foi enviado recentemente para este usuário. Aguarde antes de reenviar.");
        }
        emitir(u, "Link de redefinição de senha enviado por " + solicitante);
    }

    private void emitir(Usuario u, String descricao) {
        int minutos = props.security().tokenRecuperacaoMinutos();
        String token = tokens.emitir(u, TipoToken.RECUPERACAO_SENHA, Duration.ofMinutes(minutos));
        comunicacao.recuperacaoSenha(u, token, minutos);
        auditoria.registrarPara(u.getEmpresa().getId(), u.getId(), u.getNome(), u.getEmail(), AcaoLog.SENHA_RECUPERACAO,
                "Usuario", u.getId(), descricao, true);
    }

    @Transactional(readOnly = true)
    public boolean tokenValido(String token) {
        return tokens.validar(token, TipoToken.RECUPERACAO_SENHA).isPresent();
    }

    @Transactional
    public void redefinir(String token, String novaSenha, String confirmacao) {
        TokenUsuario t = tokens.validar(token, TipoToken.RECUPERACAO_SENHA)
                .orElseThrow(() -> new NegocioException("Link inválido ou expirado. Solicite uma nova redefinição."));
        if (!novaSenha.equals(confirmacao)) throw new NegocioException("A confirmação não confere com a nova senha.");
        Usuario u = t.getUsuario();
        u.setSenhaHash(encoder.encode(novaSenha));
        u.setTentativasLoginFalhas(0);
        u.setBloqueadoAte(null);
        // quem recebeu o link no e-mail comprovou o endereço
        if (u.getEmailVerificadoEm() == null) u.setEmailVerificadoEm(LocalDateTime.now(clock));
        usuarioRepo.save(u);
        tokens.consumir(t);
        sessaoService.expirarUsuario(u.getId());
        sessaoService.revogarLembrarAcesso(u.getEmail());
        comunicacao.senhaAlterada(u, RequestUtils.ipCliente());
        auditoria.registrarPara(u.getEmpresa().getId(), u.getId(), u.getNome(), u.getEmail(), AcaoLog.SENHA_ALTERADA,
                "Usuario", u.getId(), "Senha redefinida por link de recuperação", true);
    }
}
