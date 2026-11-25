package br.com.vigentra.service.relatorio;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Dados consolidados de um relatório (já filtrados pela empresa do usuário). */
public record RelatorioDados(
        String empresaNome,
        LocalDateTime geradoEm,
        String geradoPor,
        String descricaoFiltros,
        LocalDate hoje,
        int diasProximo,
        Map<StatusDocumento, Long> totais,
        List<LinhaMes> porMes,
        List<LinhaAgrupada> porCategoria,
        List<LinhaAgrupada> porResponsavel,
        List<Documento> documentos) {

    public long total() {
        return totais.values().stream().mapToLong(Long::longValue).sum();
    }

    public long regulares() { return totais.getOrDefault(StatusDocumento.REGULAR, 0L); }
    public long proximos() { return totais.getOrDefault(StatusDocumento.PROXIMO_VENCIMENTO, 0L); }
    public long vencidos() { return totais.getOrDefault(StatusDocumento.VENCIDO, 0L); }
    public long semVencimento() { return totais.getOrDefault(StatusDocumento.SEM_VENCIMENTO, 0L); }

    public record LinhaMes(String rotulo, int ano, int mes, long vencidos, long aVencer) {
        public long total() { return vencidos + aVencer; }
    }

    public record LinhaAgrupada(String nome, String detalhe, String cor, long total, long regulares,
                                long proximos, long vencidos, long semVencimento) {}
}
