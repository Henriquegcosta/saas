package br.com.vigentra.repository;

import br.com.vigentra.domain.model.Responsavel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResponsavelRepository extends JpaRepository<Responsavel, Long> {

    @Query("select r from Responsavel r where r.empresa.id = :empresaId order by r.nome")
    List<Responsavel> findTodos(@Param("empresaId") Long empresaId);

    @Query("select r from Responsavel r where r.empresa.id = :empresaId and r.ativo = true order by r.nome")
    List<Responsavel> findAtivos(@Param("empresaId") Long empresaId);

    @Query("select r from Responsavel r where r.empresa.id = :empresaId " +
           "and (:q is null or lower(r.nome) like :q or lower(r.email) like :q or lower(r.cargo) like :q)")
    Page<Responsavel> buscar(@Param("empresaId") Long empresaId, @Param("q") String q, Pageable pageable);

    @Query("select r from Responsavel r where r.id = :id and r.empresa.id = :empresaId")
    Optional<Responsavel> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("select r from Responsavel r where r.empresa.id = :empresaId and lower(r.email) = lower(:email)")
    Optional<Responsavel> findByEmail(@Param("empresaId") Long empresaId, @Param("email") String email);

    @Query("select r from Responsavel r where r.usuario.id = :usuarioId")
    List<Responsavel> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    /** Busca em TODAS as empresas: usada apenas pelo webhook do WhatsApp para atender pedidos de descadastro. */
    @Query("select r from Responsavel r join fetch r.empresa where r.celular = :celular")
    List<Responsavel> findByCelularTodasEmpresas(@Param("celular") String celular);

    @Query("select r from Responsavel r join fetch r.empresa left join fetch r.usuario where r.id = :id")
    Optional<Responsavel> findComEmpresa(@Param("id") Long id);

    @Query("select count(d) from Documento d where d.responsavel.id = :responsavelId")
    long contarDocumentos(@Param("responsavelId") Long responsavelId);
}
