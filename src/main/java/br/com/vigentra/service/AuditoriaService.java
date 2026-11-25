package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.model.LogAtividade;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.LogAtividadeRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.util.RequestUtils;
import br.com.vigentra.util.TextoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Trilha de auditoria (log de atividades).
 *
 * Se houver uma transação em andamento, o registro é gravado somente APÓS o commit
 * (assim só registramos ações que realmente aconteceram e não disputamos locks).
 * Sem transação (ex.: falha de login), grava imediatamente numa transação própria.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final LogAtividadeRepository repo;
    private final EmpresaRepository empresaRepo;
    private final UsuarioRepository usuarioRepo;
    private final AuditoriaService self;

    public AuditoriaService(LogAtividadeRepository repo, EmpresaRepository empresaRepo,
                            UsuarioRepository usuarioRepo, @Lazy AuditoriaService self) {
        this.repo = repo;
        this.empresaRepo = empresaRepo;
        this.usuarioRepo = usuarioRepo;
        this.self = self;
    }

    /** Registra uma ação do usuário logado. */
    public void registrar(AcaoLog acao, String entidade, Long entidadeId, String descricao) {
        UsuarioPrincipal u = SecurityUtils.usuarioOpcional().orElse(null);
        if (u == null) {
            log.warn("Auditoria sem usuário autenticado: {} - {}", acao, descricao);
            return;
        }
        registrarPara(u.getEmpresaId(), u.getId(), u.getNome(), u.getEmail(), acao, entidade, entidadeId, descricao, true);
    }

    /** Registra uma ação com empresa/usuário explícitos (login, rotinas automáticas...). */
    public void registrarPara(Long empresaId, Long usuarioId, String usuarioNome, String usuarioEmail,
                              AcaoLog acao, String entidade, Long entidadeId, String descricao, boolean sucesso) {
        String ip = RequestUtils.ipCliente();
        Runnable gravacao = () -> self.gravar(empresaId, usuarioId, usuarioNome, usuarioEmail, acao, entidade,
                entidadeId, descricao, sucesso, ip);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { gravacao.run(); }
            });
        } else {
            gravacao.run();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void gravar(Long empresaId, Long usuarioId, String usuarioNome, String usuarioEmail, AcaoLog acao,
                       String entidade, Long entidadeId, String descricao, boolean sucesso, String ip) {
        try {
            LogAtividade l = new LogAtividade();
            if (empresaId != null) l.setEmpresa(empresaRepo.getReferenceById(empresaId));
            if (usuarioId != null && usuarioRepo.existsById(usuarioId)) l.setUsuario(usuarioRepo.getReferenceById(usuarioId));
            l.setUsuarioNome(usuarioNome != null ? usuarioNome : "Sistema");
            l.setUsuarioEmail(usuarioEmail);
            l.setAcao(acao);
            l.setEntidade(entidade);
            l.setEntidadeId(entidadeId);
            l.setDescricao(truncar(descricao, 1000));
            l.setIp(ip);
            l.setSucesso(sucesso);
            repo.save(l);
        } catch (RuntimeException e) {
            // a auditoria nunca deve derrubar a operação principal
            log.error("Falha ao gravar log de atividade {}: {}", acao, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<LogAtividade> buscar(String q, AcaoLog acao, Long usuarioId, LocalDate de, LocalDate ate, int pagina, int tamanho) {
        LocalDateTime inicio = de != null ? de.atStartOfDay() : null;
        LocalDateTime fim = ate != null ? ate.plusDays(1).atStartOfDay() : null;
        return repo.buscar(SecurityUtils.empresaId(), TextoUtils.like(q), acao, usuarioId, inicio, fim,
                PageRequest.of(Math.max(pagina, 0), tamanho, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** Mantém os registros históricos ao excluir um usuário (nome/e-mail ficam copiados no log). */
    @Transactional
    public void desvincularUsuario(Usuario u) {
        repo.desvincularUsuario(u.getId());
    }

    private static String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
