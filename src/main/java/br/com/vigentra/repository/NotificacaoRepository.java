package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.model.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    @Query("select n from Notificacao n left join fetch n.documento " +
           "where n.usuario.id = :usuarioId and n.empresa.id = :empresaId " +
           "and (:lida is null or n.lida = :lida) and (:tipo is null or n.tipo = :tipo)")
    Page<Notificacao> buscar(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId,
                             @Param("lida") Boolean lida, @Param("tipo") TipoAlerta tipo, Pageable pageable);

    @Query("select count(n) from Notificacao n where n.usuario.id = :usuarioId and n.lida = false")
    long contarNaoLidas(@Param("usuarioId") Long usuarioId);

    @Query("select n from Notificacao n where n.usuario.id = :usuarioId and n.lida = false order by n.createdAt desc")
    List<Notificacao> findNaoLidas(@Param("usuarioId") Long usuarioId, Pageable pageable);

    @Query("select n from Notificacao n left join fetch n.documento where n.id = :id and n.usuario.id = :usuarioId")
    Optional<Notificacao> findByIdAndUsuarioId(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    boolean existsByUsuarioIdAndChave(Long usuarioId, String chave);

    @Modifying
    @Query("update Notificacao n set n.lida = true, n.lidaEm = :agora where n.usuario.id = :usuarioId and n.lida = false")
    int marcarTodasComoLidas(@Param("usuarioId") Long usuarioId, @Param("agora") LocalDateTime agora);

    @Modifying
    @Query("delete from Notificacao n where n.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("delete from Notificacao n where n.documento.id = :documentoId")
    void deleteByDocumentoId(@Param("documentoId") Long documentoId);
}
