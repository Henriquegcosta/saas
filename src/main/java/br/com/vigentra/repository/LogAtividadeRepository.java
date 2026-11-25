package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.model.LogAtividade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LogAtividadeRepository extends JpaRepository<LogAtividade, Long> {

    @Query("select l from LogAtividade l where l.empresa.id = :empresaId " +
           "and (:q is null or lower(l.descricao) like :q or lower(l.usuarioNome) like :q or lower(l.usuarioEmail) like :q) " +
           "and (:acao is null or l.acao = :acao) " +
           "and (:usuarioId is null or l.usuario.id = :usuarioId) " +
           "and (:de is null or l.createdAt >= :de) and (:ate is null or l.createdAt < :ate)")
    Page<LogAtividade> buscar(@Param("empresaId") Long empresaId, @Param("q") String q, @Param("acao") AcaoLog acao,
                              @Param("usuarioId") Long usuarioId, @Param("de") LocalDateTime de,
                              @Param("ate") LocalDateTime ate, Pageable pageable);

    @Modifying
    @Query("update LogAtividade l set l.usuario = null where l.usuario.id = :usuarioId")
    void desvincularUsuario(@Param("usuarioId") Long usuarioId);
}
