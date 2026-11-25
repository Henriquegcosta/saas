package br.com.vigentra.repository;

import br.com.vigentra.domain.model.BloqueioAgendador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface BloqueioAgendadorRepository extends JpaRepository<BloqueioAgendador, String> {

    /** Tenta assumir a trava de forma ATÔMICA: só atualiza se ela estiver livre (expirada). */
    @Modifying
    @Query("update BloqueioAgendador b set b.bloqueadoAte = :ate, b.bloqueadoEm = :agora, b.bloqueadoPor = :por " +
           "where b.nome = :nome and b.bloqueadoAte <= :agora")
    int assumir(@Param("nome") String nome, @Param("ate") LocalDateTime ate, @Param("agora") LocalDateTime agora,
                @Param("por") String por);

    @Modifying
    @Query("update BloqueioAgendador b set b.bloqueadoAte = :agora where b.nome = :nome and b.bloqueadoPor = :por")
    int liberar(@Param("nome") String nome, @Param("agora") LocalDateTime agora, @Param("por") String por);
}
