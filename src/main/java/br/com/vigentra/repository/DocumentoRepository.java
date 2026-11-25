package br.com.vigentra.repository;

import br.com.vigentra.domain.model.Documento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Consultas dinâmicas (filtros/pesquisa) usam Specifications - ver
 * {@link br.com.vigentra.repository.DocumentoSpecs}. Todas incluem empresa_id.
 */
public interface DocumentoRepository extends JpaRepository<Documento, Long>, JpaSpecificationExecutor<Documento> {

    /** Carrega categoria e responsável junto (evita N+1 nas listagens). */
    @Override
    @EntityGraph(attributePaths = {"categoria", "responsavel"})
    Page<Documento> findAll(Specification<Documento> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"categoria", "responsavel"})
    List<Documento> findAll(Specification<Documento> spec, Sort sort);

    @Query("select d from Documento d left join fetch d.categoria left join fetch d.responsavel " +
           "where d.id = :id and d.empresa.id = :empresaId")
    Optional<Documento> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /** Documentos que podem gerar alerta: possuem vencimento até a data-limite informada. */
    @Query("select d from Documento d join fetch d.categoria left join fetch d.responsavel r left join fetch r.usuario " +
           "where d.empresa.id = :empresaId and d.semVencimento = false and d.dataVencimento is not null " +
           "and d.dataVencimento <= :limite order by d.dataVencimento")
    List<Documento> findAlertaveis(@Param("empresaId") Long empresaId, @Param("limite") LocalDate limite);

    @Query("select d from Documento d join fetch d.categoria left join fetch d.responsavel r left join fetch r.usuario " +
           "where d.id = :id and d.empresa.id = :empresaId")
    Optional<Documento> findParaAlerta(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("select count(d) from Documento d where d.categoria.id = :categoriaId")
    long contarPorCategoria(@Param("categoriaId") Long categoriaId);

    @Modifying
    @Query("update Documento d set d.criadoPor = null where d.criadoPor.id = :usuarioId")
    void desvincularCriador(@Param("usuarioId") Long usuarioId);
}
