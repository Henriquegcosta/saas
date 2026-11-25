package br.com.vigentra.repository;

import br.com.vigentra.domain.model.Convite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConviteRepository extends JpaRepository<Convite, Long> {

    @Query("select c from Convite c join fetch c.empresa left join fetch c.convidadoPor where c.tokenHash = :hash")
    Optional<Convite> findByTokenHash(@Param("hash") String hash);

    @Query("select c from Convite c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Convite> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /** Convites ainda não aceitos nem cancelados (os expirados também aparecem, para reenvio). */
    @Query("select c from Convite c left join fetch c.convidadoPor where c.empresa.id = :empresaId " +
           "and c.aceitoEm is null and c.canceladoEm is null order by c.createdAt desc")
    List<Convite> findAbertos(@Param("empresaId") Long empresaId);

    @Query("select c from Convite c where c.empresa.id = :empresaId and lower(c.email) = lower(:email) " +
           "and c.aceitoEm is null and c.canceladoEm is null")
    List<Convite> findAbertosPorEmail(@Param("empresaId") Long empresaId, @Param("email") String email);

    @Modifying
    @Query("update Convite c set c.convidadoPor = null where c.convidadoPor.id = :usuarioId")
    void desvincularConvidador(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("update Convite c set c.usuario = null where c.usuario.id = :usuarioId")
    void desvincularUsuario(@Param("usuarioId") Long usuarioId);
}
