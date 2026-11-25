package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {

    Optional<Perfil> findByCodigo(PerfilCodigo codigo);

    List<Perfil> findAllByOrderByNivelDesc();
}
