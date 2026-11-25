package br.com.vigentra.service.conta;

import br.com.vigentra.domain.enums.TipoToken;
import br.com.vigentra.domain.model.TokenUsuario;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.TokenUsuarioRepository;
import br.com.vigentra.util.RequestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Tokens de uso único (confirmação de e-mail, troca de e-mail, recuperação de senha):
 * 256 bits aleatórios (SecureRandom), apenas o hash SHA-256 é persistido,
 * validade limitada, tokens anteriores do mesmo tipo são invalidados e o uso é marcado.
 */
@Service
public class TokenService {

    private final SecureRandom random = new SecureRandom();
    private final TokenUsuarioRepository repo;
    private final Clock clock;

    public TokenService(TokenUsuarioRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Gera um token e devolve o valor BRUTO (vai apenas no link do e-mail; nunca é salvo). */
    @Transactional
    public String emitir(Usuario u, TipoToken tipo, Duration validade) {
        LocalDateTime agora = LocalDateTime.now(clock);
        repo.invalidarAnteriores(u.getId(), tipo, agora);
        String bruto = aleatorio();
        TokenUsuario t = new TokenUsuario();
        t.setUsuario(u);
        t.setTipo(tipo);
        t.setTokenHash(sha256(bruto));
        t.setExpiraEm(agora.plus(validade == null ? tipo.getValidade() : validade));
        t.setIpSolicitacao(RequestUtils.ipCliente());
        repo.save(t);
        return bruto;
    }

    @Transactional(readOnly = true)
    public Optional<TokenUsuario> validar(String bruto, TipoToken tipo) {
        if (bruto == null || bruto.length() < 20 || bruto.length() > 100) return Optional.empty();
        return repo.findByHash(sha256(bruto), tipo).filter(t -> t.isValido(LocalDateTime.now(clock)));
    }

    /** Marca como usado (dentro da transação de quem consome). */
    public void consumir(TokenUsuario t) {
        t.setUsadoEm(LocalDateTime.now(clock));
        repo.save(t);
    }

    @Transactional(readOnly = true)
    public LocalDateTime ultimoEmitido(Usuario u, TipoToken tipo) {
        return repo.ultimoEmitido(u.getId(), tipo);
    }

    public String aleatorio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
