package br.com.vigentra.service.alerta;

import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.model.Configuracao;

import java.util.List;
import java.util.Optional;

/**
 * Período de alerta em que um documento se encontra hoje.
 *
 * <p>Regra (com a configuração padrão 30, 15, 7 e 0 dias):</p>
 * <pre>
 *  faltam 16..30 dias  -> D30   (alerta "30 dias antes")
 *  faltam  8..15 dias  -> D15
 *  faltam  1..7  dias  -> D7
 *  vence hoje          -> D0
 *  vencido há 1..7     -> POS1  (1º aviso pós-vencimento, intervalo configurável)
 *  vencido há 8..14    -> POS2  ... até o máximo configurado
 * </pre>
 * Usar FAIXAS (e não o dia exato) garante que o alerta seja enviado mesmo que a rotina
 * não rode em algum dia (servidor desligado) ou que o documento seja cadastrado já
 * perto do vencimento. A deduplicação por (documento, período, vencimento, destinatário)
 * garante que cada período gere no máximo um envio.
 */
public record PeriodoAlerta(String codigo, TipoAlerta tipo, int limiar) {

    public static Optional<PeriodoAlerta> calcular(long diasParaVencer, Configuracao cfg) {
        return calcular(diasParaVencer, cfg.getDiasAlertaLista(), cfg.isAlertarAposVencimento(),
                cfg.getIntervaloPosVencimento(), cfg.getMaxAlertasPosVencimento());
    }

    public static Optional<PeriodoAlerta> calcular(long dias, List<Integer> limiares, boolean aposVencimento,
                                                   int intervalo, int maxPos) {
        if (dias >= 0) {
            return limiares.stream().filter(l -> l >= dias).min(Integer::compare)
                    .map(l -> l == 0 ? new PeriodoAlerta("D0", TipoAlerta.VENCE_HOJE, 0)
                                     : new PeriodoAlerta("D" + l, TipoAlerta.ANTECEDENCIA, l));
        }
        if (!aposVencimento || intervalo <= 0) return Optional.empty();
        long atraso = -dias;
        int aviso = (int) ((atraso - 1) / intervalo) + 1;
        if (aviso > maxPos) return Optional.empty();
        return Optional.of(new PeriodoAlerta("POS" + aviso, TipoAlerta.VENCIDO, aviso));
    }
}
