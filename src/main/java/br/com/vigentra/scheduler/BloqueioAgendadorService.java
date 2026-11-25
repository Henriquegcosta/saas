package br.com.vigentra.scheduler;

import br.com.vigentra.domain.model.BloqueioAgendador;
import br.com.vigentra.repository.BloqueioAgendadorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Trava distribuída simples (no MySQL) para as rotinas agendadas.
 * A trava é assumida com um UPDATE condicional ATÔMICO ("só se estiver expirada"),
 * então duas instâncias nunca executam a mesma rotina ao mesmo tempo. O tempo máximo
 * evita trava "presa" caso a instância caia no meio da execução.
 */
@Service
public class BloqueioAgendadorService {

    private static final Logger log = LoggerFactory.getLogger(BloqueioAgendadorService.class);

    private final BloqueioAgendadorRepository repo;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String instancia;

    public BloqueioAgendadorService(BloqueioAgendadorRepository repo, PlatformTransactionManager tm, Clock clock) {
        this.repo = repo;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.instancia = identificar();
    }

    /** Executa a tarefa se conseguir a trava; caso contrário, não faz nada e devolve null. */
    public <T> T executarComTrava(String nome, Duration maximo, Supplier<T> tarefa) {
        if (!assumir(nome, maximo)) {
            log.info("Rotina '{}' já está em execução em outra instância/agendamento - ignorando.", nome);
            return null;
        }
        try {
            return tarefa.get();
        } finally {
            tx.executeWithoutResult(s -> repo.liberar(nome, LocalDateTime.now(clock), instancia));
        }
    }

    boolean assumir(String nome, Duration maximo) {
        LocalDateTime agora = LocalDateTime.now(clock);
        Boolean criou = null;
        if (Boolean.FALSE.equals(tx.execute(s -> repo.existsById(nome)))) {
            try {
                criou = tx.execute(s -> {
                    repo.saveAndFlush(new BloqueioAgendador(nome, agora.plus(maximo), agora, instancia));
                    return true;
                });
            } catch (DataIntegrityViolationException e) {
                criou = false; // outra instância criou ao mesmo tempo
            }
        }
        if (Boolean.TRUE.equals(criou)) return true;
        Integer n = tx.execute(s -> repo.assumir(nome, agora.plus(maximo), agora, instancia));
        return n != null && n == 1;
    }

    private static String identificar() {
        try {
            return InetAddress.getLocalHost().getHostName() + "/" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception e) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }
}
