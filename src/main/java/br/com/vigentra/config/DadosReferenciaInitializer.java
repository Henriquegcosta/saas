package br.com.vigentra.config;

import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.Perfil;
import br.com.vigentra.domain.model.Permissao;
import br.com.vigentra.repository.PerfilRepository;
import br.com.vigentra.repository.PermissaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante, na inicialização, que as tabelas de referência (perfis e permissões)
 * contenham todos os valores dos enums. É idempotente: só insere o que faltar.
 * (O script.sql já cria esses dados; isto protege contra versões novas de permissões.)
 */
@Component
public class DadosReferenciaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosReferenciaInitializer.class);

    private final PerfilRepository perfilRepo;
    private final PermissaoRepository permissaoRepo;

    public DadosReferenciaInitializer(PerfilRepository perfilRepo, PermissaoRepository permissaoRepo) {
        this.perfilRepo = perfilRepo;
        this.permissaoRepo = permissaoRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (PerfilCodigo pc : PerfilCodigo.values()) {
            if (perfilRepo.findByCodigo(pc).isEmpty()) {
                perfilRepo.save(new Perfil(pc));
                log.info("Perfil de referência criado: {}", pc);
            }
        }
        for (PermissaoCodigo p : PermissaoCodigo.values()) {
            if (permissaoRepo.findByCodigo(p).isEmpty()) {
                permissaoRepo.save(new Permissao(p));
                log.info("Permissão de referência criada: {}", p);
            }
        }
    }
}
