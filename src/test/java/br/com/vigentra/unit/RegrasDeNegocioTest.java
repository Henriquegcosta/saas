package br.com.vigentra.unit;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.service.StatusCalculator;
import br.com.vigentra.service.alerta.PeriodoAlerta;
import br.com.vigentra.util.TextoUtils;
import br.com.vigentra.domain.model.Documento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes unitários das regras de negócio puras (sem Spring). */
class RegrasDeNegocioTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 14);

    @Nested
    @DisplayName("Cálculo da situação do documento")
    class CalculoDeVencimento {

        @ParameterizedTest(name = "vence em {0} dia(s) -> {1}")
        @CsvSource({
                "-30, VENCIDO",
                "-1,  VENCIDO",
                "0,   PROXIMO_VENCIMENTO",
                "1,   PROXIMO_VENCIMENTO",
                "30,  PROXIMO_VENCIMENTO",
                "31,  REGULAR",
                "365, REGULAR"})
        void calculaSituacao(int dias, StatusDocumento esperado) {
            assertThat(StatusCalculator.calcular(HOJE.plusDays(dias), false, HOJE, 30)).isEqualTo(esperado);
        }

        @Test
        void semVencimento() {
            assertThat(StatusCalculator.calcular(null, true, HOJE, 30)).isEqualTo(StatusDocumento.SEM_VENCIMENTO);
            assertThat(StatusCalculator.calcular(null, false, HOJE, 30)).isEqualTo(StatusDocumento.SEM_VENCIMENTO);
            // marcado como "sem vencimento" ignora qualquer data
            assertThat(StatusCalculator.calcular(HOJE.minusDays(10), true, HOJE, 30)).isEqualTo(StatusDocumento.SEM_VENCIMENTO);
        }

        @Test
        void janelaConfiguravel() {
            assertThat(StatusCalculator.calcular(HOJE.plusDays(45), false, HOJE, 60)).isEqualTo(StatusDocumento.PROXIMO_VENCIMENTO);
            assertThat(StatusCalculator.calcular(HOJE.plusDays(45), false, HOJE, 30)).isEqualTo(StatusDocumento.REGULAR);
        }
    }

    @Nested
    @DisplayName("Régua de alertas (30, 15, 7, 0 e pós-vencimento)")
    class Regua {

        private final List<Integer> padrao = List.of(0, 7, 15, 30);

        @ParameterizedTest(name = "faltam {0} dia(s) -> {1}")
        @CsvSource({
                "30, D30", "16, D30", "15, D15", "8, D15", "7, D7", "1, D7", "0, D0",
                "-1, POS1", "-7, POS1", "-8, POS2", "-21, POS3"})
        void periodoAtual(int dias, String esperado) {
            assertThat(PeriodoAlerta.calcular(dias, padrao, true, 7, 3)).get()
                    .extracting(PeriodoAlerta::codigo).isEqualTo(esperado);
        }

        @Test
        void foraDaRegua() {
            assertThat(PeriodoAlerta.calcular(31, padrao, true, 7, 3)).isEmpty();
            assertThat(PeriodoAlerta.calcular(-22, padrao, true, 7, 3)).isEmpty(); // excedeu o máximo de avisos
            assertThat(PeriodoAlerta.calcular(-3, padrao, false, 7, 3)).isEmpty(); // pós-vencimento desativado
        }

        @Test
        void tipos() {
            assertThat(PeriodoAlerta.calcular(10, padrao, true, 7, 3).orElseThrow().tipo()).isEqualTo(TipoAlerta.ANTECEDENCIA);
            assertThat(PeriodoAlerta.calcular(0, padrao, true, 7, 3).orElseThrow().tipo()).isEqualTo(TipoAlerta.VENCE_HOJE);
            assertThat(PeriodoAlerta.calcular(-2, padrao, true, 7, 3).orElseThrow().tipo()).isEqualTo(TipoAlerta.VENCIDO);
        }
    }

    @Nested
    @DisplayName("Validações de texto")
    class Textos {
        @Test
        void cnpj() {
            assertThat(TextoUtils.cnpjValido("11.222.333/0001-81")).isTrue();
            assertThat(TextoUtils.cnpjValido("11.222.333/0001-82")).isFalse();
            assertThat(TextoUtils.cnpjValido("11111111111111")).isFalse();
            assertThat(TextoUtils.formatarCnpj("11222333000181")).isEqualTo("11.222.333/0001-81");
        }

        @Test
        void likeEscapaCuringas() {
            assertThat(TextoUtils.like(" 50%_off ")).isEqualTo("%50\\%\\_off%");
            assertThat(TextoUtils.like("  ")).isNull();
        }
    }

    @Nested
    @DisplayName("Prazos amigáveis e utilidades de texto")
    class PrazosETextos {
        @Test
        void descreverTodosOsEstadosDePrazo() {
            Documento documento = new Documento();
            documento.setDataVencimento(HOJE.plusDays(5));
            assertThat(StatusCalculator.diasParaVencer(documento, HOJE)).isEqualTo(5L);
            assertThat(StatusCalculator.descreverPrazo(documento, HOJE)).isEqualTo("Vence em 5 dias");

            documento.setDataVencimento(HOJE.plusDays(1));
            assertThat(StatusCalculator.descreverPrazo(documento, HOJE)).isEqualTo("Vence amanhã");

            documento.setDataVencimento(HOJE);
            assertThat(StatusCalculator.descreverPrazo(documento, HOJE)).isEqualTo("Vence hoje");

            documento.setDataVencimento(HOJE.minusDays(1));
            assertThat(StatusCalculator.descreverPrazo(documento, HOJE)).isEqualTo("Venceu ontem");

            documento.setDataVencimento(HOJE.minusDays(3));
            assertThat(StatusCalculator.descreverPrazo(documento, HOJE)).isEqualTo("Vencido há 3 dias");
        }

        @Test
        void ausenciaDeDataRetornaPrazoNulo() {
            Documento documento = new Documento();
            assertThat(StatusCalculator.diasParaVencer(documento, HOJE)).isNull();
            assertThat(StatusCalculator.descreverPrazo(documento, HOJE)).isEqualTo("Sem data de vencimento");

            documento.setSemVencimento(true);
            assertThat(StatusCalculator.diasParaVencer(documento, HOJE)).isNull();
            assertThat(StatusCalculator.descreverPrazo(documento, HOJE)).isEqualTo("Sem data de vencimento");
        }

        @Test
        void textoLimpoESlug() {
            assertThat(TextoUtils.limpar(null)).isNull();
            assertThat(TextoUtils.limpar("   ")).isNull();
            assertThat(TextoUtils.limpar("  Conteúdo  ")).isEqualTo("Conteúdo");
            assertThat(TextoUtils.somenteDigitos("(11) 99999-0001")).isEqualTo("11999990001");
            assertThat(TextoUtils.formatarCnpj("incorreto")).isEqualTo("incorreto");
            assertThat(TextoUtils.slug("  Ação Comercial 2026!  ")).isEqualTo("acao-comercial-2026");
            assertThat(TextoUtils.slug(null)).isEqualTo("arquivo");
        }
    }
}
