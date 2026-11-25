package br.com.vigentra.service;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.model.HistoricoEnvio;
import br.com.vigentra.domain.model.Notificacao;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.repository.NotificacaoRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.util.TextoUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Notificações do usuário (sino) e consulta ao histórico de envios da empresa. */
@Service
public class NotificacaoService {

    private final NotificacaoRepository repo;
    private final HistoricoEnvioRepository historicoRepo;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository repo, HistoricoEnvioRepository historicoRepo, Clock clock) {
        this.repo = repo;
        this.historicoRepo = historicoRepo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Notificacao> minhas(Boolean lida, TipoAlerta tipo, int pagina, int tamanho) {
        UsuarioPrincipal u = SecurityUtils.usuario();
        return repo.buscar(u.getEmpresaId(), u.getId(), lida, tipo,
                PageRequest.of(Math.max(pagina, 0), tamanho, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public long naoLidas() {
        return SecurityUtils.usuarioOpcional().map(u -> repo.contarNaoLidas(u.getId())).orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<Notificacao> recentesNaoLidas(int limite) {
        return repo.findNaoLidas(SecurityUtils.usuario().getId(), PageRequest.of(0, limite));
    }

    /** Marca como lida e devolve a notificação (para redirecionar ao documento). Só do próprio usuário. */
    @Transactional
    public Notificacao marcarLida(Long id) {
        Notificacao n = repo.findByIdAndUsuarioId(id, SecurityUtils.usuario().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação não encontrada."));
        if (!n.isLida()) {
            n.setLida(true);
            n.setLidaEm(LocalDateTime.now(clock));
        }
        return n;
    }

    @Transactional
    public int marcarTodasLidas() {
        return repo.marcarTodasComoLidas(SecurityUtils.usuario().getId(), LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public Page<HistoricoEnvio> historico(StatusEnvio status, TipoAlerta tipo, CanalEnvio canal, TipoMensagem tipoMensagem,
                                          String q, int pagina, int tamanho) {
        return historicoRepo.buscar(SecurityUtils.empresaId(), status, tipo, canal, tipoMensagem, TextoUtils.like(q),
                PageRequest.of(Math.max(pagina, 0), tamanho, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public List<HistoricoEnvio> historicoRecente(int limite) {
        return historicoRepo.findRecentes(SecurityUtils.empresaId(), PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public List<HistoricoEnvio> historicoDoDocumento(Long documentoId) {
        return historicoRepo.findByDocumento(documentoId, SecurityUtils.empresaId());
    }

    @Transactional(readOnly = true)
    public Map<StatusEnvio, Long> contagemEnvios() {
        Map<StatusEnvio, Long> m = new EnumMap<>(StatusEnvio.class);
        for (StatusEnvio s : StatusEnvio.values()) m.put(s, 0L);
        for (Object[] row : historicoRepo.contarPorStatus(SecurityUtils.empresaId())) {
            m.put((StatusEnvio) row[0], (Long) row[1]);
        }
        return m;
    }

    /** Quantidade de mensagens por canal e situação ("EMAIL:ENVIADO" -> n). empresaId null = todas (plataforma). */
    @Transactional(readOnly = true)
    public Map<String, Long> contagemPorCanal(Long empresaId) {
        Map<String, Long> m = new java.util.HashMap<>();
        for (Object[] row : historicoRepo.contarPorCanalEStatus(empresaId)) {
            String canal = ((CanalEnvio) row[0]).name();
            String st = ((StatusEnvio) row[1]).name();
            long n = (Long) row[2];
            m.merge(canal + ":" + st, n, Long::sum);
            m.merge(canal + ":TOTAL", n, Long::sum);
            m.merge("TOTAL:" + st, n, Long::sum);
            m.merge("TOTAL:TOTAL", n, Long::sum);
        }
        return m;
    }

    public Map<String, Long> contagemPorCanal() {
        return contagemPorCanal(SecurityUtils.empresaId());
    }
}
