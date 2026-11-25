package br.com.vigentra.service;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Regra de negócio: cálculo automático da situação do documento.
 *
 * <pre>
 *  sem vencimento              -> SEM_VENCIMENTO
 *  vencimento &lt; hoje           -> VENCIDO
 *  hoje &lt;= venc &lt;= hoje + N    -> PROXIMO_VENCIMENTO   (N configurável, padrão 30 dias)
 *  vencimento &gt; hoje + N       -> REGULAR
 * </pre>
 */
public final class StatusCalculator {

    private StatusCalculator() {}

    public static StatusDocumento calcular(LocalDate vencimento, boolean semVencimento, LocalDate hoje, int diasProximo) {
        if (semVencimento || vencimento == null) return StatusDocumento.SEM_VENCIMENTO;
        if (vencimento.isBefore(hoje)) return StatusDocumento.VENCIDO;
        if (!vencimento.isAfter(hoje.plusDays(diasProximo))) return StatusDocumento.PROXIMO_VENCIMENTO;
        return StatusDocumento.REGULAR;
    }

    public static StatusDocumento calcular(Documento d, LocalDate hoje, int diasProximo) {
        return calcular(d.getDataVencimento(), d.isSemVencimento(), hoje, diasProximo);
    }

    /** Dias até o vencimento (negativo = dias em atraso). Null se não houver vencimento. */
    public static Long diasParaVencer(Documento d, LocalDate hoje) {
        if (d.isSemVencimento() || d.getDataVencimento() == null) return null;
        return ChronoUnit.DAYS.between(hoje, d.getDataVencimento());
    }

    /** Texto amigável: "vence hoje", "vence em 5 dias", "vencido há 3 dias". */
    public static String descreverPrazo(Documento d, LocalDate hoje) {
        Long dias = diasParaVencer(d, hoje);
        if (dias == null) return "Sem data de vencimento";
        if (dias == 0) return "Vence hoje";
        if (dias == 1) return "Vence amanhã";
        if (dias > 1) return "Vence em " + dias + " dias";
        if (dias == -1) return "Venceu ontem";
        return "Vencido há " + (-dias) + " dias";
    }
}
