package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.TipoToken;
import br.com.vigentra.domain.model.TokenUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {

    @Query("select t from TokenUsuario t join fetch t.usuario u join fetch u.empresa where t.tokenHash = :hash and t.tipo = :tipo")
    Optional<TokenUsuario> findByHash(@Param("hash") String hash, @Param("tipo") TipoToken tipo);

    /** Invalida tokens anteriores ainda não usados do mesmo tipo (só o mais recente vale). */
    @Modifying
    @Query("update TokenUsuario t set t.usadoEm = :agora where t.usuario.id = :usuarioId and t.tipo = :tipo and t.usadoEm is null")
    void invalidarAnteriores(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoToken tipo, @Param("agora") LocalDateTime agora);

    @Query("select max(t.createdAt) from TokenUsuario t where t.usuario.id = :usuarioId and t.tipo = :tipo")
    LocalDateTime ultimoEmitido(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoToken tipo);

    @Modifying
    @Query("delete from TokenUsuario t where t.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}
