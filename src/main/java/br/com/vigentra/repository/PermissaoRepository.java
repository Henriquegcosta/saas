package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.Permissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissaoRepository extends JpaRepository<Permissao, Long> {

    Optional<Permissao> findByCodigo(PermissaoCodigo codigo);
}
