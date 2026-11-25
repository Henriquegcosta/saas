package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.model.Responsavel;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.comunicacao.PreferenciasService;
import br.com.vigentra.util.TextoUtils;
import br.com.vigentra.web.dto.ResponsavelForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Responsáveis pelos documentos (destinatários dos alertas). */
@Service
public class ResponsavelService {

    private final ResponsavelRepository repo;
    private final UsuarioRepository usuarioRepo;
    private final EmpresaRepository empresaRepo;
    private final AuditoriaService auditoria;
    private final PreferenciasService preferenciasService;

    public ResponsavelService(ResponsavelRepository repo, UsuarioRepository usuarioRepo, EmpresaRepository empresaRepo,
                              AuditoriaService auditoria, PreferenciasService preferenciasService) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
        this.empresaRepo = empresaRepo;
        this.auditoria = auditoria;
        this.preferenciasService = preferenciasService;
    }

    @Transactional(readOnly = true)
    public List<Responsavel> ativos() {
        return repo.findAtivos(SecurityUtils.empresaId());
    }

    @Transactional(readOnly = true)
    public List<Responsavel> todos() {
        return repo.findTodos(SecurityUtils.empresaId());
    }

    @Transactional(readOnly = true)
    public Page<Responsavel> buscar(String q, int pagina, int tamanho) {
        return repo.buscar(SecurityUtils.empresaId(), TextoUtils.like(q),
                PageRequest.of(Math.max(pagina, 0), tamanho, Sort.by("nome")));
    }

    @Transactional(readOnly = true)
    public Responsavel buscar(Long id) {
        return repo.findByIdAndEmpresaId(id, SecurityUtils.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável não encontrado."));
    }

    public long contarDocumentos(Long id) {
        return repo.contarDocumentos(id);
    }

    @Transactional
    public Responsavel salvar(ResponsavelForm f) {
        Long empresaId = SecurityUtils.empresaId();
        String email = f.getEmail().trim().toLowerCase();
        repo.findByEmail(empresaId, email).ifPresent(existente -> {
            if (!existente.getId().equals(f.getId())) throw new NegocioException("Já existe um responsável com este e-mail.");
        });
        Responsavel r = f.getId() == null ? new Responsavel() : buscar(f.getId());
        boolean novo = r.getId() == null;
        if (novo) r.setEmpresa(empresaRepo.getReferenceById(empresaId));
        r.setNome(f.getNome().trim());
        r.setEmail(email);
        r.setTelefone(TextoUtils.limpar(f.getTelefone()));
        r.setCargo(TextoUtils.limpar(f.getCargo()));
        r.setAtivo(f.isAtivo());
        if (f.getUsuarioId() != null) {
            // o usuário vinculado também precisa ser da mesma empresa
            Usuario u = usuarioRepo.findByIdAndEmpresaId(f.getUsuarioId(), empresaId)
                    .orElseThrow(() -> new NegocioException("Usuário vinculado inválido."));
            r.setUsuario(u);
        } else {
            r.setUsuario(null);
        }
        if (r.getUsuario() == null && f.getPreferencias() != null) {
            preferenciasService.aplicarPeloGestor(r, f.getPreferencias());
        }
        repo.save(r);
        auditoria.registrar(AcaoLog.RESPONSAVEL_ALTERADO, "Responsavel", r.getId(),
                (novo ? "Responsável cadastrado: " : "Responsável alterado: ") + r.getNome() + " <" + r.getEmail() + ">");
        return r;
    }

    @Transactional
    public void alternarAtivo(Long id) {
        Responsavel r = buscar(id);
        r.setAtivo(!r.isAtivo());
        auditoria.registrar(AcaoLog.RESPONSAVEL_ALTERADO, "Responsavel", id,
                "Responsável " + r.getNome() + (r.isAtivo() ? " reativado" : " desativado"));
    }

    @Transactional
    public void excluir(Long id) {
        Responsavel r = buscar(id);
        long docs = repo.contarDocumentos(id);
        if (docs > 0) throw new NegocioException("Este responsável possui " + docs
                + " documento(s) vinculado(s). Transfira os documentos ou desative o responsável.");
        repo.delete(r);
        auditoria.registrar(AcaoLog.RESPONSAVEL_ALTERADO, "Responsavel", id, "Responsável excluído: " + r.getNome());
    }

    /**
     * Usado no formulário de documento: localiza o responsável pelo e-mail
     * (dentro da empresa) ou cria um novo automaticamente.
     */
    @Transactional
    public Responsavel resolver(String nome, String email) {
        String n = TextoUtils.limpar(nome);
        String e = TextoUtils.limpar(email);
        if (n == null && e == null) return null;
        if (e == null) throw new NegocioException("Informe o e-mail do responsável para que ele receba os alertas.");
        Long empresaId = SecurityUtils.empresaId();
        return repo.findByEmail(empresaId, e).orElseGet(() -> {
            Responsavel r = new Responsavel();
            r.setEmpresa(empresaRepo.getReferenceById(empresaId));
            r.setNome(n != null ? n : e);
            r.setEmail(e.toLowerCase());
            repo.save(r);
            auditoria.registrar(AcaoLog.RESPONSAVEL_ALTERADO, "Responsavel", r.getId(),
                    "Responsável cadastrado automaticamente: " + r.getNome() + " <" + r.getEmail() + ">");
            return r;
        });
    }
}
