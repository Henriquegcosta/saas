package br.com.vigentra.service.relatorio;

import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.repository.CategoriaRepository;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.StatusCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Consolida os dados dos relatórios. Parte sempre de {@link DocumentoService#aplicarFiltro},
 * herdando o isolamento por empresa e as regras de visibilidade.
 */
@Service
public class RelatorioService {

    private static final Locale PT = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentoService documentoService;
    private final CategoriaRepository categoriaRepo;
    private final ResponsavelRepository responsavelRepo;
    private final Clock clock;

    public RelatorioService(DocumentoService documentoService, CategoriaRepository categoriaRepo,
                            ResponsavelRepository responsavelRepo, Clock clock) {
        this.documentoService = documentoService;
        this.categoriaRepo = categoriaRepo;
        this.responsavelRepo = responsavelRepo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RelatorioDados gerar(DocumentoFiltro filtro) {
        SecurityUtils.exigir(PermissaoCodigo.REPORT_VIEW);
        UsuarioPrincipal u = SecurityUtils.usuario();
        LocalDate hoje = documentoService.hoje();
        int dias = documentoService.diasProximo();
        List<Documento> docs = documentoService.listarTodos(filtro);

        Map<StatusDocumento, Long> totais = new EnumMap<>(StatusDocumento.class);
        for (StatusDocumento s : StatusDocumento.values()) totais.put(s, 0L);
        docs.forEach(d -> totais.merge(StatusCalculator.calcular(d, hoje, dias), 1L, Long::sum));

        return new RelatorioDados(u.getEmpresaNome(), LocalDateTime.now(clock), u.getNome(), descrever(filtro), hoje, dias,
                totais, porMes(docs, filtro, hoje),
                agrupar(docs, d -> d.getCategoria().getNome(), d -> d.getCategoria().getDescricao(), d -> d.getCategoria().getCor(), hoje, dias),
                agrupar(docs, d -> d.getResponsavel() != null ? d.getResponsavel().getNome() : "Sem responsável",
                        d -> d.getResponsavel() != null ? d.getResponsavel().getEmail() : "", d -> null, hoje, dias),
                docs);
    }

    /** Vencimentos por mês: 12 meses a partir do início do filtro (ou do mês atual - 2). */
    private List<RelatorioDados.LinhaMes> porMes(List<Documento> docs, DocumentoFiltro f, LocalDate hoje) {
        YearMonth inicio = f.getVencimentoDe() != null ? YearMonth.from(f.getVencimentoDe()) : YearMonth.from(hoje).minusMonths(2);
        YearMonth fim = f.getVencimentoAte() != null ? YearMonth.from(f.getVencimentoAte()) : inicio.plusMonths(11);
        if (fim.isBefore(inicio)) fim = inicio;
        if (inicio.plusMonths(35).isBefore(fim)) fim = inicio.plusMonths(35);

        Map<YearMonth, long[]> mapa = new LinkedHashMap<>();
        for (YearMonth m = inicio; !m.isAfter(fim); m = m.plusMonths(1)) mapa.put(m, new long[2]);
        for (Documento d : docs) {
            if (d.getDataVencimento() == null) continue;
            long[] c = mapa.get(YearMonth.from(d.getDataVencimento()));
            if (c == null) continue;
            if (d.getDataVencimento().isBefore(hoje)) c[0]++; else c[1]++;
        }
        List<RelatorioDados.LinhaMes> l = new ArrayList<>();
        mapa.forEach((m, c) -> l.add(new RelatorioDados.LinhaMes(
                m.getMonth().getDisplayName(TextStyle.SHORT, PT).replace(".", "") + "/" + String.valueOf(m.getYear()).substring(2),
                m.getYear(), m.getMonthValue(), c[0], c[1])));
        return l;
    }

    private List<RelatorioDados.LinhaAgrupada> agrupar(List<Documento> docs, Function<Documento, String> chave,
                                                       Function<Documento, String> detalhe, Function<Documento, String> cor,
                                                       LocalDate hoje, int dias) {
        Map<String, long[]> cont = new LinkedHashMap<>();
        Map<String, String[]> meta = new LinkedHashMap<>();
        for (Documento d : docs) {
            String k = chave.apply(d);
            long[] c = cont.computeIfAbsent(k, x -> new long[5]);
            meta.putIfAbsent(k, new String[]{detalhe.apply(d), cor.apply(d)});
            c[0]++;
            switch (StatusCalculator.calcular(d, hoje, dias)) {
                case REGULAR -> c[1]++;
                case PROXIMO_VENCIMENTO -> c[2]++;
                case VENCIDO -> c[3]++;
                case SEM_VENCIMENTO -> c[4]++;
            }
        }
        List<RelatorioDados.LinhaAgrupada> l = new ArrayList<>();
        cont.forEach((k, c) -> l.add(new RelatorioDados.LinhaAgrupada(k, meta.get(k)[0], meta.get(k)[1], c[0], c[1], c[2], c[3], c[4])));
        l.sort(Comparator.comparingLong(RelatorioDados.LinhaAgrupada::total).reversed());
        return l;
    }

    private String descrever(DocumentoFiltro f) {
        List<String> p = new ArrayList<>();
        Long empresaId = SecurityUtils.empresaId();
        if (f.getCategoriaId() != null) categoriaRepo.findByIdAndEmpresaId(f.getCategoriaId(), empresaId)
                .ifPresent(c -> p.add("Categoria: " + c.getNome()));
        if (f.getResponsavelId() != null) responsavelRepo.findByIdAndEmpresaId(f.getResponsavelId(), empresaId)
                .ifPresent(r -> p.add("Responsável: " + r.getNome()));
        if (f.getStatus() != null) p.add("Situação: " + f.getStatus().getLabel());
        if (f.getVencimentoDe() != null) p.add("Vencimento a partir de " + f.getVencimentoDe().format(BR));
        if (f.getVencimentoAte() != null) p.add("Vencimento até " + f.getVencimentoAte().format(BR));
        if (f.getQ() != null && !f.getQ().isBlank()) p.add("Busca: \"" + f.getQ().trim() + "\"");
        return p.isEmpty() ? "Todos os documentos" : String.join(" · ", p);
    }
}
