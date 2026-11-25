package br.com.vigentra.repository;

import br.com.vigentra.domain.model.RegraCanalAlerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RegraCanalAlertaRepository extends JpaRepository<RegraCanalAlerta, Long> {

    @Query("select r from RegraCanalAlerta r where r.empresa.id = :empresaId")
    List<RegraCanalAlerta> findByEmpresa(@Param("empresaId") Long empresaId);
}
