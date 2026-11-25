package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Categoria;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.Responsavel;
import br.com.vigentra.repository.CategoriaRepository;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.repository.DocumentoRepository;
import br.com.vigentra.repository.DocumentoSpecs;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.repository.NotificacaoRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.util.TextoUtils;
import br.com.vigentra.web.dto.DocumentoForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Regras de negócio dos documentos. Toda consulta parte de {@link #base()},
 * que aplica o filtro da empresa (tenant) e a visibilidade conforme as permissões.
 */
@Service
public class DocumentoService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentoRepository repo;
    private final CategoriaRepository categoriaRepo;
    private final EmpresaRepository empresaRepo;
    private final UsuarioRepository usuarioRepo;
    private final NotificacaoRepository notificacaoRepo;
    private final HistoricoEnvioRepository historicoRepo;
    private final ResponsavelService responsavelService;
    private final ArmazenamentoService armazenamento;
    private final ConfiguracaoService configuracaoService;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public DocumentoService(DocumentoRepository repo, CategoriaRepository categoriaRepo, EmpresaRepository empresaRepo,
                            UsuarioRepository usuarioRepo, NotificacaoRepository notificacaoRepo,
                            HistoricoEnvioRepository historicoRepo, ResponsavelService responsavelService,
                            ArmazenamentoService armazenamento, ConfiguracaoService configuracaoService,
                            AuditoriaService auditoria, Clock clock) {
        this.repo = repo;
        this.categoriaRepo = categoriaRepo;
        this.empresaRepo = empresaRepo;
        this.usuarioRepo = usuarioRepo;
        this.notificacaoRepo = notificacaoRepo;
        this.historicoRepo = historicoRepo;
        this.responsavelService = responsavelService;
        this.armazenamento = armazenamento;
        this.configuracaoService = configuracaoService;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public int diasProximo() {
        return configuracaoService.atual().getDiasProximoVencimento();
    }

    /** Filtro obrigatório: empresa do usuário + visibilidade. */
    public Specification<Documento> base() {
        UsuarioPrincipal u = SecurityUtils.usuario();
        Specification<Documento> spec = DocumentoSpecs.daEmpresa(u.getEmpresaId());
        if (!u.tem(PermissaoCodigo.DOC_VIEW_ALL)) spec = spec.and(DocumentoSpecs.visivelPara(u.getId()));
        return spec;
    }

    public Specification<Documento> aplicarFiltro(DocumentoFiltro f) {
        return base()
                .and(DocumentoSpecs.texto(f.getQ()))
                .and(DocumentoSpecs.categoria(f.getCategoriaId()))
                .and(DocumentoSpecs.responsavel(f.getResponsavelId()))
                .and(DocumentoSpecs.empresaResponsavel(f.getEmpresaResponsavel()))
                .and(DocumentoSpecs.vencimentoEntre(f.getVencimentoDe(), f.getVencimentoAte()))
                .and(DocumentoSpecs.comStatus(f.getStatus(), hoje(), diasProximo()));
    }

    public static Sort ordenacao(String ordem) {
        if (ordem == null) ordem = "vencimento";
        return switch (ordem) {
            case "vencimento_desc" -> Sort.by(Sort.Order.asc("semVencimento"), Sort.Order.desc("dataVencimento"));
            case "nome" -> Sort.by("nome");
            case "recentes" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Order.asc("semVencimento"), Sort.Order.asc("dataVencimento"), Sort.Order.asc("nome"));
        };
    }

    @Transactional(readOnly = true)
    public Page<Documento> listar(DocumentoFiltro filtro, int pagina, int tamanho) {
        return repo.findAll(aplicarFiltro(filtro), PageRequest.of(Math.max(pagina, 0), tamanho, ordenacao(filtro.getOrdem())));
    }

    @Transactional(readOnly = true)
    public List<Documento> listarTodos(DocumentoFiltro filtro) {
        return repo.findAll(aplicarFiltro(filtro), ordenacao(filtro.getOrdem()));
    }

    /** Busca respeitando tenant e visibilidade; documento de outra empresa = 404. */
    @Transactional(readOnly = true)
    public Documento buscar(Long id) {
        Documento d = repo.findByIdAndEmpresaId(id, SecurityUtils.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
        if (!SecurityUtils.tem(PermissaoCodigo.DOC_VIEW_ALL) && repo.count(base().and((r, q, cb) -> cb.equal(r.get("id"), id))) == 0) {
            throw new RecursoNaoEncontradoException("Documento não encontrado.");
        }
        return d;
    }

    @Transactional
    public Documento salvar(DocumentoForm f, MultipartFile arquivo) {
        UsuarioPrincipal u = SecurityUtils.usuario();
        boolean novo = f.getId() == null;
        SecurityUtils.exigir(novo ? PermissaoCodigo.DOC_CREATE : PermissaoCodigo.DOC_EDIT);

        // ---- validações de negócio ----
        if (!f.isSemVencimento() && f.getDataVencimento() == null) {
            throw new NegocioException("Informe a data de vencimento ou marque \"Documento sem vencimento\".");
        }
        if (!f.isSemVencimento() && f.getDataEmissao() != null && f.getDataVencimento().isBefore(f.getDataEmissao())) {
            throw new NegocioException("A data de vencimento não pode ser anterior à data de emissão.");
        }
        if (f.getDataEmissao() != null && f.getDataEmissao().isAfter(hoje().plusDays(1))) {
            throw new NegocioException("A data de emissão não pode estar no futuro.");
        }
        Categoria categoria = categoriaRepo.findByIdAndEmpresaId(f.getCategoriaId(), u.getEmpresaId())
                .orElseThrow(() -> new NegocioException("Categoria inválida."));
        Responsavel responsavel = responsavelService.resolver(f.getResponsavelNome(), f.getResponsavelEmail());

        Documento d = novo ? new Documento() : buscar(f.getId());
        List<String> alteracoes = novo ? List.of() : diferencas(d, f, categoria, responsavel);

        if (novo) {
            d.setEmpresa(empresaRepo.getReferenceById(u.getEmpresaId()));
            d.setCriadoPor(usuarioRepo.getReferenceById(u.getId()));
        }
        d.setNome(f.getNome().trim());
        d.setCategoria(categoria);
        d.setResponsavel(responsavel);
        d.setNumero(TextoUtils.limpar(f.getNumero()));
        d.setDescricao(TextoUtils.limpar(f.getDescricao()));
        d.setEmpresaResponsavel(TextoUtils.limpar(f.getEmpresaResponsavel()));
        d.setDataEmissao(f.getDataEmissao());
        d.setSemVencimento(f.isSemVencimento());
        d.setDataVencimento(f.isSemVencimento() ? null : f.getDataVencimento());
        d.setObservacoes(TextoUtils.limpar(f.getObservacoes()));

        // ---- anexo ----
        String arquivoAntigo = null;
        if (arquivo != null && !arquivo.isEmpty()) {
            var salvo = armazenamento.salvar(u.getEmpresaId(), arquivo);
            arquivoAntigo = d.getArquivoArmazenado();
            d.setArquivoArmazenado(salvo.nomeArmazenado());
            d.setArquivoNomeOriginal(salvo.nomeOriginal());
            d.setArquivoTipo(salvo.tipo());
            d.setArquivoTamanho(salvo.tamanho());
            if (!novo) alteracoes = adicionar(alteracoes, "anexo substituído (" + salvo.nomeOriginal() + ")");
            // se a transação falhar, remove o arquivo recém-gravado para não deixar lixo
            aposRollback(() -> armazenamento.excluir(u.getEmpresaId(), salvo.nomeArmazenado()));
        } else if (f.isRemoverArquivo() && d.temArquivo()) {
            arquivoAntigo = d.getArquivoArmazenado();
            d.setArquivoArmazenado(null);
            d.setArquivoNomeOriginal(null);
            d.setArquivoTipo(null);
            d.setArquivoTamanho(null);
            alteracoes = adicionar(alteracoes, "anexo removido");
        }
        repo.save(d);
        if (arquivoAntigo != null) {
            String antigo = arquivoAntigo;
            aposCommit(() -> armazenamento.excluir(u.getEmpresaId(), antigo));
        }

        if (novo) {
            auditoria.registrar(AcaoLog.DOCUMENTO_CRIADO, "Documento", d.getId(),
                    "Documento cadastrado: \"" + d.getNome() + "\" (" + categoria.getNome() + ", "
                            + (d.isSemVencimento() ? "sem vencimento" : "vence em " + d.getDataVencimento().format(BR)) + ")");
        } else {
            auditoria.registrar(AcaoLog.DOCUMENTO_ALTERADO, "Documento", d.getId(),
                    "Documento alterado: \"" + d.getNome() + "\"" + (alteracoes.isEmpty() ? " (sem mudanças de conteúdo)"
                            : " - " + String.join("; ", alteracoes)));
        }
        return d;
    }

    @Transactional
    public void excluir(Long id) {
        SecurityUtils.exigir(PermissaoCodigo.DOC_DELETE);
        Documento d = buscar(id);
        Long empresaId = d.getEmpresa().getId();
        String arquivo = d.getArquivoArmazenado();
        notificacaoRepo.deleteByDocumentoId(id);
        historicoRepo.desvincularDocumento(id); // histórico de envios é preservado
        repo.delete(d);
        if (arquivo != null) aposCommit(() -> armazenamento.excluir(empresaId, arquivo));
        auditoria.registrar(AcaoLog.DOCUMENTO_EXCLUIDO, "Documento", id,
                "Documento excluído: \"" + d.getNome() + "\" (" + d.getCategoria().getNome() + ")");
    }

    // ------------------------------------------------------------------ indicadores

    @Transactional(readOnly = true)
    public Map<StatusDocumento, Long> contagemPorStatus() {
        return contagemPorStatus(base());
    }

    @Transactional(readOnly = true)
    public Map<StatusDocumento, Long> contagemPorStatus(Specification<Documento> spec) {
        LocalDate hoje = hoje();
        int dias = diasProximo();
        Map<StatusDocumento, Long> m = new EnumMap<>(StatusDocumento.class);
        for (StatusDocumento s : StatusDocumento.values()) {
            m.put(s, repo.count(spec.and(DocumentoSpecs.comStatus(s, hoje, dias))));
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<Documento> proximosVencimentos(int limite, int diasAFrente) {
        LocalDate hoje = hoje();
        return repo.findAll(base().and(DocumentoSpecs.vencimentoEntre(hoje, hoje.plusDays(diasAFrente))),
                PageRequest.of(0, limite, ordenacao("vencimento"))).getContent();
    }

    @Transactional(readOnly = true)
    public List<Documento> vencidos(int limite) {
        return repo.findAll(base().and(DocumentoSpecs.comStatus(StatusDocumento.VENCIDO, hoje(), diasProximo())),
                PageRequest.of(0, limite, ordenacao("vencimento"))).getContent();
    }

    @Transactional(readOnly = true)
    public List<Documento> ultimosCadastrados(int limite) {
        return repo.findAll(base(), PageRequest.of(0, limite, ordenacao("recentes"))).getContent();
    }

    @Transactional(readOnly = true)
    public List<Documento> vencendoEntre(LocalDate de, LocalDate ate) {
        return repo.findAll(base().and(DocumentoSpecs.vencimentoEntre(de, ate)), ordenacao("vencimento"));
    }

    public StatusDocumento status(Documento d) {
        return StatusCalculator.calcular(d, hoje(), diasProximo());
    }

    // ------------------------------------------------------------------ utilitários

    private List<String> diferencas(Documento d, DocumentoForm f, Categoria c, Responsavel r) {
        List<String> m = new ArrayList<>();
        if (!Objects.equals(d.getNome(), f.getNome().trim())) m.add("nome: \"" + d.getNome() + "\" → \"" + f.getNome().trim() + "\"");
        if (!Objects.equals(d.getCategoria().getId(), c.getId())) m.add("categoria: " + d.getCategoria().getNome() + " → " + c.getNome());
        LocalDate novoVenc = f.isSemVencimento() ? null : f.getDataVencimento();
        if (!Objects.equals(d.getDataVencimento(), novoVenc)) {
            m.add("vencimento: " + fmt(d.getDataVencimento()) + " → " + fmt(novoVenc));
        }
        if (!Objects.equals(d.getDataEmissao(), f.getDataEmissao())) m.add("emissão: " + fmt(d.getDataEmissao()) + " → " + fmt(f.getDataEmissao()));
        Long respAntigo = d.getResponsavel() != null ? d.getResponsavel().getId() : null;
        Long respNovo = r != null ? r.getId() : null;
        if (!Objects.equals(respAntigo, respNovo)) {
            m.add("responsável: " + (d.getResponsavel() != null ? d.getResponsavel().getNome() : "-") + " → " + (r != null ? r.getNome() : "-"));
        }
        if (!Objects.equals(d.getNumero(), TextoUtils.limpar(f.getNumero()))) m.add("número alterado");
        if (!Objects.equals(d.getDescricao(), TextoUtils.limpar(f.getDescricao()))) m.add("descrição alterada");
        if (!Objects.equals(d.getObservacoes(), TextoUtils.limpar(f.getObservacoes()))) m.add("observações alteradas");
        if (!Objects.equals(d.getEmpresaResponsavel(), TextoUtils.limpar(f.getEmpresaResponsavel()))) m.add("empresa responsável alterada");
        return m;
    }

    private static List<String> adicionar(List<String> l, String s) {
        List<String> n = new ArrayList<>(l);
        n.add(s);
        return n;
    }

    private static String fmt(LocalDate d) {
        return d == null ? "sem data" : d.format(BR);
    }

    private static void aposCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else r.run();
    }

    private static void aposRollback(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) r.run(); }
            });
        }
    }
}
