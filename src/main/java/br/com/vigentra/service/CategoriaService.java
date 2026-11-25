package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.model.Categoria;
import br.com.vigentra.domain.model.Empresa;
import br.com.vigentra.repository.CategoriaRepository;
import br.com.vigentra.repository.DocumentoRepository;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.web.dto.CategoriaForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriaService {

    /** Categorias criadas automaticamente para toda nova empresa. */
    private static final String[][] PADRAO = {
            {"Alvarás e Licenças", "Alvarás de funcionamento, licenças sanitárias e ambientais", "#2563EB", "bi-building-check"},
            {"Certidões", "Certidões negativas de débitos (federal, estadual, municipal, FGTS, trabalhista)", "#7C3AED", "bi-patch-check"},
            {"Contratos", "Contratos com clientes, fornecedores e locação", "#0891B2", "bi-file-earmark-text"},
            {"CNH e Habilitações", "Carteiras de habilitação de motoristas e colaboradores", "#EA580C", "bi-car-front"},
            {"Certificados Digitais", "Certificados e-CNPJ, e-CPF (A1/A3)", "#16A34A", "bi-shield-lock"},
            {"Documentos de Funcionários", "ASO, NRs, treinamentos e documentos pessoais", "#DB2777", "bi-people"},
            {"Certificados Profissionais", "Registros em conselhos, certificações técnicas", "#CA8A04", "bi-award"},
            {"Documentos Empresariais", "Contrato social, inscrições e registros da empresa", "#475569", "bi-briefcase"},
    };

    private final CategoriaRepository repo;
    private final DocumentoRepository documentoRepo;
    private final EmpresaRepository empresaRepo;
    private final AuditoriaService auditoria;

    public CategoriaService(CategoriaRepository repo, DocumentoRepository documentoRepo,
                            EmpresaRepository empresaRepo, AuditoriaService auditoria) {
        this.repo = repo;
        this.documentoRepo = documentoRepo;
        this.empresaRepo = empresaRepo;
        this.auditoria = auditoria;
    }

    @Transactional
    public void criarPadrao(Empresa empresa) {
        for (String[] c : PADRAO) repo.save(new Categoria(empresa, c[0], c[1], c[2], c[3]));
    }

    @Transactional(readOnly = true)
    public List<Categoria> ativas() {
        return repo.findAtivas(SecurityUtils.empresaId());
    }

    @Transactional(readOnly = true)
    public List<Categoria> todas() {
        return repo.findTodas(SecurityUtils.empresaId());
    }

    @Transactional(readOnly = true)
    public Categoria buscar(Long id) {
        return repo.findByIdAndEmpresaId(id, SecurityUtils.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
    }

    @Transactional
    public Categoria salvar(CategoriaForm f) {
        Long empresaId = SecurityUtils.empresaId();
        String nome = f.getNome().trim();
        if (repo.existeNome(empresaId, nome, f.getId())) throw new NegocioException("Já existe uma categoria com esse nome.");
        Categoria c = f.getId() == null ? new Categoria() : buscar(f.getId());
        boolean nova = c.getId() == null;
        if (nova) c.setEmpresa(empresaRepo.getReferenceById(empresaId));
        c.setNome(nome);
        c.setDescricao(f.getDescricao());
        c.setCor(f.getCor());
        c.setIcone(f.getIcone());
        repo.save(c);
        auditoria.registrar(AcaoLog.CATEGORIA_ALTERADA, "Categoria", c.getId(),
                (nova ? "Categoria criada: " : "Categoria alterada: ") + nome);
        return c;
    }

    @Transactional
    public void alternarAtivo(Long id) {
        Categoria c = buscar(id);
        c.setAtivo(!c.isAtivo());
        auditoria.registrar(AcaoLog.CATEGORIA_ALTERADA, "Categoria", id,
                "Categoria " + c.getNome() + (c.isAtivo() ? " reativada" : " desativada"));
    }

    @Transactional
    public void excluir(Long id) {
        Categoria c = buscar(id);
        long docs = documentoRepo.contarPorCategoria(id);
        if (docs > 0) throw new NegocioException("A categoria possui " + docs + " documento(s). Desative-a em vez de excluir.");
        repo.delete(c);
        auditoria.registrar(AcaoLog.CATEGORIA_ALTERADA, "Categoria", id, "Categoria excluída: " + c.getNome());
    }

    public long contarDocumentos(Long categoriaId) {
        return documentoRepo.contarPorCategoria(categoriaId);
    }
}
