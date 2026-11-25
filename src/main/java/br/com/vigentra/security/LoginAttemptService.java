package br.com.vigentra.security;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limitação de tentativas de login em duas camadas:
 *  1) por CONTA: após N falhas seguidas a conta fica bloqueada por X minutos (persistido no banco);
 *  2) por IP: muitas falhas do mesmo IP em 15 min bloqueiam novas tentativas (em memória).
 */
@Service
public class LoginAttemptService {

    private static final Duration JANELA_IP = Duration.ofMinutes(15);

    private final UsuarioRepository usuarioRepo;
    private final AppProperties.Security cfg;
    private final Clock clock;
    private final Map<String, Deque<Instant>> falhasPorIp = new ConcurrentHashMap<>();

    public LoginAttemptService(UsuarioRepository usuarioRepo, AppProperties props, Clock clock) {
        this.usuarioRepo = usuarioRepo;
        this.cfg = props.security();
        this.clock = clock;
    }

    /** @return true se a conta foi bloqueada nesta tentativa. */
    @Transactional
    public boolean registrarFalha(String email, String ip) {
        if (ip != null) {
            falhasPorIp.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>()).addLast(clock.instant());
        }
        if (email == null) return false;
        return usuarioRepo.findByEmailIgnoreCase(email.trim()).map(u -> {
            int tentativas = u.getTentativasLoginFalhas() + 1;
            boolean bloqueou = false;
            if (tentativas >= cfg.maxTentativasLogin()) {
                u.setBloqueadoAte(LocalDateTime.now(clock).plusMinutes(cfg.bloqueioMinutos()));
                tentativas = 0;
                bloqueou = true;
            }
            u.setTentativasLoginFalhas(tentativas);
            usuarioRepo.save(u);
            return bloqueou;
        }).orElse(false);
    }

    @Transactional
    public void registrarSucesso(Long usuarioId, String ip) {
        if (ip != null) falhasPorIp.remove(ip);
        usuarioRepo.findById(usuarioId).ifPresent(u -> {
            u.setTentativasLoginFalhas(0);
            u.setBloqueadoAte(null);
            u.setUltimoLogin(LocalDateTime.now(clock));
            usuarioRepo.save(u);
        });
    }

    public boolean ipBloqueado(String ip) {
        if (ip == null) return false;
        Deque<Instant> falhas = falhasPorIp.get(ip);
        if (falhas == null) return false;
        Instant limite = clock.instant().minus(JANELA_IP);
        while (!falhas.isEmpty() && falhas.peekFirst().isBefore(limite)) falhas.pollFirst();
        return falhas.size() >= cfg.maxTentativasIp();
    }

    /** Usado nos testes. */
    public void limpar() { falhasPorIp.clear(); }
}
