package br.com.vigentra.repository;

import br.com.vigentra.domain.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    @Query("select c from Categoria c where c.empresa.id = :empresaId order by c.nome")
    List<Categoria> findTodas(@Param("empresaId") Long empresaId);

    @Query("select c from Categoria c where c.empresa.id = :empresaId and c.ativo = true order by c.nome")
    List<Categoria> findAtivas(@Param("empresaId") Long empresaId);

    @Query("select c from Categoria c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Categoria> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("select count(c) > 0 from Categoria c where c.empresa.id = :empresaId and lower(c.nome) = lower(:nome) " +
           "and (:ignorarId is null or c.id <> :ignorarId)")
    boolean existeNome(@Param("empresaId") Long empresaId, @Param("nome") String nome, @Param("ignorarId") Long ignorarId);
}
