package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.PerfilPermissao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerfilPermissaoRepository extends JpaRepository<PerfilPermissao, Long> {

    @Query("select pp.permissao.codigo from PerfilPermissao pp " +
           "where pp.empresa.id = :empresaId and pp.perfil.id = :perfilId")
    List<PermissaoCodigo> findCodigos(@Param("empresaId") Long empresaId, @Param("perfilId") Long perfilId);

    @Query("select pp from PerfilPermissao pp where pp.empresa.id = :empresaId")
    List<PerfilPermissao> findByEmpresaId(@Param("empresaId") Long empresaId);

    @Modifying
    @Query("delete from PerfilPermissao pp where pp.empresa.id = :empresaId and pp.perfil.id = :perfilId")
    void deleteByEmpresaAndPerfil(@Param("empresaId") Long empresaId, @Param("perfilId") Long perfilId);
}
