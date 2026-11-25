package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u join fetch u.empresa where lower(u.email) = lower(:email)")
    Optional<Usuario> findByEmailComEmpresa(@Param("email") String email);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from Usuario u where u.id = :id and u.empresa.id = :empresaId")
    Optional<Usuario> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("select u from Usuario u where u.empresa.id = :empresaId " +
           "and (:q is null or lower(u.nome) like :q or lower(u.email) like :q) " +
           "and (:perfil is null or u.perfil.codigo = :perfil) " +
           "and (:ativo is null or u.ativo = :ativo)")
    Page<Usuario> buscar(@Param("empresaId") Long empresaId, @Param("q") String q,
                         @Param("perfil") PerfilCodigo perfil, @Param("ativo") Boolean ativo, Pageable pageable);

    @Query("select u from Usuario u where u.empresa.id = :empresaId and u.ativo = true order by u.nome")
    List<Usuario> findAtivos(@Param("empresaId") Long empresaId);

    @Query("select u from Usuario u where u.empresa.id = :empresaId order by u.nome")
    List<Usuario> findTodos(@Param("empresaId") Long empresaId);

    /** Busca em TODAS as empresas: usada apenas pelo webhook do WhatsApp para atender pedidos de descadastro. */
    @Query("select u from Usuario u join fetch u.empresa where u.celular = :celular")
    List<Usuario> findByCelularTodasEmpresas(@Param("celular") String celular);

    @Query("select u from Usuario u where u.empresa.id = :empresaId and u.perfil.codigo = :perfil and u.ativo = true")
    List<Usuario> findAtivosPorPerfil(@Param("empresaId") Long empresaId, @Param("perfil") PerfilCodigo perfil);

    @Query("select count(u) from Usuario u where u.empresa.id = :empresaId and u.perfil.codigo = :perfil and u.ativo = true")
    long countAtivosPorPerfil(@Param("empresaId") Long empresaId, @Param("perfil") PerfilCodigo perfil);
}
