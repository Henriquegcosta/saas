package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.Empresa;
import br.com.vigentra.domain.model.Perfil;
import br.com.vigentra.domain.model.PerfilPermissao;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.PerfilPermissaoRepository;
import br.com.vigentra.repository.PerfilRepository;
import br.com.vigentra.repository.PermissaoRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.SessaoService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Matriz de permissões por empresa (perfil x permissão). */
@Service
public class PermissaoService {

    private final PerfilRepository perfilRepo;
    private final PermissaoRepository permissaoRepo;
    private final PerfilPermissaoRepository ppRepo;
    private final EmpresaRepository empresaRepo;
    private final AuditoriaService auditoria;
    private final SessaoService sessaoService;

    public PermissaoService(PerfilRepository perfilRepo, PermissaoRepository permissaoRepo,
                            PerfilPermissaoRepository ppRepo, EmpresaRepository empresaRepo,
                            AuditoriaService auditoria, SessaoService sessaoService) {
        this.perfilRepo = perfilRepo;
        this.permissaoRepo = permissaoRepo;
        this.ppRepo = ppRepo;
        this.empresaRepo = empresaRepo;
        this.auditoria = auditoria;
        this.sessaoService = sessaoService;
    }

    /** Permissões efetivas de um perfil na empresa. O Administrador sempre tem acesso total. */
    @Transactional(readOnly = true)
    public Set<PermissaoCodigo> permissoesDe(Long empresaId, Perfil perfil) {
        if (perfil.getCodigo() == PerfilCodigo.ADMIN) return EnumSet.allOf(PermissaoCodigo.class);
        var lista = ppRepo.findCodigos(empresaId, perfil.getId());
        return lista.isEmpty() ? EnumSet.noneOf(PermissaoCodigo.class) : EnumSet.copyOf(lista);
    }

    /** Cria a matriz padrão para uma empresa recém-cadastrada. */
    @Transactional
    public void inicializarEmpresa(Empresa empresa) {
        for (PerfilCodigo pc : PerfilCodigo.values()) {
            Perfil perfil = perfil(pc);
            for (PermissaoCodigo p : PermissaoCodigo.padrao(pc)) {
                ppRepo.save(new PerfilPermissao(empresa, perfil, permissaoRepo.findByCodigo(p).orElseThrow()));
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<PerfilCodigo, Set<PermissaoCodigo>> matriz() {
        Long empresaId = SecurityUtils.empresaId();
        Map<PerfilCodigo, Set<PermissaoCodigo>> m = new EnumMap<>(PerfilCodigo.class);
        for (PerfilCodigo pc : PerfilCodigo.values()) m.put(pc, permissoesDe(empresaId, perfil(pc)));
        return m;
    }

    /** Somente o Administrador altera permissões; o perfil Administrador não pode ser restringido. */
    @Transactional
    public void atualizar(PerfilCodigo perfilCodigo, Set<PermissaoCodigo> novas) {
        var usuario = SecurityUtils.usuario();
        if (!usuario.isAdmin()) throw new AccessDeniedException("Apenas administradores alteram permissões.");
        if (perfilCodigo == PerfilCodigo.ADMIN) throw new NegocioException("O perfil Administrador sempre possui acesso total.");

        Long empresaId = usuario.getEmpresaId();
        Empresa empresa = empresaRepo.getReferenceById(empresaId);
        Perfil perfil = perfil(perfilCodigo);
        ppRepo.deleteByEmpresaAndPerfil(empresaId, perfil.getId());
        ppRepo.flush();
        for (PermissaoCodigo p : novas) {
            ppRepo.save(new PerfilPermissao(empresa, perfil, permissaoRepo.findByCodigo(p).orElseThrow()));
        }
        auditoria.registrar(AcaoLog.PERMISSOES_ALTERADAS, "Perfil", perfil.getId(),
                "Permissões do perfil " + perfil.getNome() + " alteradas para: " +
                (novas.isEmpty() ? "(nenhuma)" : novas.stream().map(PermissaoCodigo::getDescricao).collect(Collectors.joining(", "))));
        // usuários desse perfil precisam entrar novamente para receber as novas permissões
        sessaoService.expirarPerfil(empresaId, perfilCodigo, usuario.getId());
    }

    public Perfil perfil(PerfilCodigo codigo) {
        return perfilRepo.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalStateException("Perfil não cadastrado: " + codigo + ". Execute o script.sql."));
    }
}
