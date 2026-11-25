package br.com.vigentra.repository;

import br.com.vigentra.domain.model.Configuracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Long> {

    @Query("select c from Configuracao c where c.empresa.id = :empresaId")
    Optional<Configuracao> findByEmpresaId(@Param("empresaId") Long empresaId);
}
