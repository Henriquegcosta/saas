package br.com.vigentra.unit;

import br.com.vigentra.service.doki.Intencao;
import br.com.vigentra.service.doki.Interpretacao;
import br.com.vigentra.service.doki.InterpretadorPorRegras;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Doki - reconhecimento das perguntas")
class DokiInterpretadorTest {

    private final InterpretadorPorRegras interpretador = new InterpretadorPorRegras();

    private Interpretacao i(String pergunta) {
        return interpretador.interpretar(pergunta);
    }

    @Nested
    @DisplayName("Perguntas sobre dados")
    class Dados {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
                "Quais documentos estão próximos do vencimento?   | DOCUMENTOS_PROXIMOS",
                "Quais documentos já venceram?                    | DOCUMENTOS_VENCIDOS",
                "Tem algum documento vencido?                     | DOCUMENTOS_VENCIDOS",
                "documentos expirados                             | DOCUMENTOS_VENCIDOS",
                "O que está atrasado?                             | DOCUMENTOS_VENCIDOS",
                "Quantos documentos vencidos eu tenho?            | DOCUMENTOS_VENCIDOS",
                "Quantos documentos estão cadastrados?            | TOTAL_DOCUMENTOS",
                "Qual o total de documentos?                      | TOTAL_DOCUMENTOS",
                "Quais certificados precisam de atenção?          | CERTIFICADOS_ATENCAO",
                "Meus certificados estão em dia?                  | CERTIFICADOS_ATENCAO",
                "QUAIS DOCUMENTOS ESTAO PROXIMOS DO VENCIMENTO    | DOCUMENTOS_PROXIMOS"})
        void consultas(String pergunta, Intencao esperada) {
            assertThat(i(pergunta).intencao()).isEqualTo(esperada);
        }

        @ParameterizedTest(name = "\"{0}\" -> {1} dias")
        @CsvSource(delimiter = '|', value = {
                "Existem documentos vencendo nos próximos 7 dias?  | 7",
                "Tem algo vencendo nos próximos 15 dias?           | 15",
                "Documentos que vencem nos próximos 30 dias        | 30",
                "O que vence esta semana?                          | 7",
                "O que vence nesta quinzena?                       | 15",
                "Algo vence este mês?                              | 30",
                "Vence alguma coisa hoje?                          | 0",
                "Vence algo amanhã?                                | 1",
                "Documentos vencendo nos próximos 999 dias         | 365"})
        void prazo(String pergunta, int dias) {
            Interpretacao r = i(pergunta);
            assertThat(r.intencao()).isEqualTo(Intencao.DOCUMENTOS_PROXIMOS);
            assertThat(r.dias()).isEqualTo(dias);
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @DisplayName("Formas diferentes, sinônimos e perguntas informais")
        @CsvSource(delimiter = '|', value = {
                // vencidos
                "Me mostra os documentos atrasados.               | DOCUMENTOS_VENCIDOS",
                "Quais estão fora da validade?                    | DOCUMENTOS_VENCIDOS",
                "Existe documento com validade expirada?          | DOCUMENTOS_VENCIDOS",
                "algum doc perdeu a validade?                     | DOCUMENTOS_VENCIDOS",
                "docs pendentes                                   | DOCUMENTOS_VENCIDOS",
                "tem algo irregular?                              | DOCUMENTOS_VENCIDOS",
                "vencidos                                         | DOCUMENTOS_VENCIDOS",
                // próximos
                "O que vai vencer?                                | DOCUMENTOS_PROXIMOS",
                "Tem documento vencendo?                          | DOCUMENTOS_PROXIMOS",
                "Quais documentos precisam ser renovados?         | DOCUMENTOS_PROXIMOS",
                "Me mostra os documentos que estão para vencer.   | DOCUMENTOS_PROXIMOS",
                "o que expira logo?                               | DOCUMENTOS_PROXIMOS",
                "próximos vencimentos                             | DOCUMENTOS_PROXIMOS",
                "documentos a vencer                              | DOCUMENTOS_PROXIMOS",
                // quantidade / visão geral
                "Quantos documentos temos?                        | TOTAL_DOCUMENTOS",
                "Me informa a quantidade cadastrada.              | TOTAL_DOCUMENTOS",
                "lista de documentos                              | TOTAL_DOCUMENTOS",
                "resumo dos documentos                            | TOTAL_DOCUMENTOS",
                "situação dos documentos                          | TOTAL_DOCUMENTOS",
                "como estão meus documentos?                      | TOTAL_DOCUMENTOS",
                "Quais documentos foram cadastrados?              | TOTAL_DOCUMENTOS",
                // certificados
                "Tem certificado vencido?                         | CERTIFICADOS_ATENCAO",
                "Quais certificados estão vencendo?               | CERTIFICADOS_ATENCAO",
                "Existe algum certificado irregular?              | CERTIFICADOS_ATENCAO",
                "e-CNPJ está em dia?                              | CERTIFICADOS_ATENCAO"})
        void variacoes(String pergunta, Intencao esperada) {
            assertThat(i(pergunta).intencao()).isEqualTo(esperada);
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @DisplayName("Pequenos erros de digitação")
        @CsvSource(delimiter = '|', value = {
                "documetos vensidos                               | DOCUMENTOS_VENCIDOS",
                "quais docuemntos venceram                        | DOCUMENTOS_VENCIDOS",
                "quantso documentos                               | TOTAL_DOCUMENTOS",
                "certifcados vencendo                             | CERTIFICADOS_ATENCAO",
                "proxmos vencimentos                              | DOCUMENTOS_PROXIMOS",
                "como funciona o calendairo                       | COMO_USAR_CALENDARIO",
                "onde ficam os relatorois                         | ONDE_RELATORIOS",
                "como cadastar um documento                       | COMO_CADASTRAR_DOCUMENTO",
                "como atualisar um documento                      | COMO_ATUALIZAR_DOCUMENTO"})
        void errosDeDigitacao(String pergunta, Intencao esperada) {
            assertThat(i(pergunta).intencao()).isEqualTo(esperada);
        }

        @Test
        @DisplayName("Sem prazo na pergunta, usa o padrão configurado da empresa (dias = null)")
        void semPrazo() {
            assertThat(i("Quais documentos estão próximos do vencimento?").dias()).isNull();
        }
    }

    @Nested
    @DisplayName("Perguntas sobre o uso do sistema")
    class Orientacoes {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
                "Como cadastro um novo documento?                 | COMO_CADASTRAR_DOCUMENTO",
                "Quero adicionar um documento                     | COMO_CADASTRAR_DOCUMENTO",
                "Como incluir uma licença?                        | COMO_CADASTRAR_DOCUMENTO",
                "Como cadastro um certificado?                    | COMO_CADASTRAR_CERTIFICADO",
                "Como registrar um certificado digital?           | COMO_CADASTRAR_CERTIFICADO",
                "Como funciona o calendário de vencimentos?       | COMO_USAR_CALENDARIO",
                "Onde encontro os relatórios?                     | ONDE_RELATORIOS",
                "Como exporto para Excel?                         | ONDE_RELATORIOS",
                "Como convido outro usuário?                      | COMO_CONVIDAR_USUARIO",
                "Quero adicionar um novo usuário                  | COMO_CONVIDAR_USUARIO",
                "Como atualizo as informações de um documento?    | COMO_ATUALIZAR_DOCUMENTO",
                "Como renovo um certificado vencido?              | COMO_ATUALIZAR_DOCUMENTO",
                "como faço para editar um documento               | COMO_ATUALIZAR_DOCUMENTO",
                "quero mudar a data de um documento               | COMO_ATUALIZAR_DOCUMENTO",
                "tem como cadastrar um alvará?                    | COMO_CADASTRAR_DOCUMENTO",
                "novo certificado                                 | COMO_CADASTRAR_CERTIFICADO",
                "quero dar acesso a um colega                     | COMO_CONVIDAR_USUARIO",
                "onde vejo os gráficos?                           | ONDE_RELATORIOS",
                "dá pra imprimir a lista?                         | ONDE_RELATORIOS",
                "agenda de vencimentos                            | COMO_USAR_CALENDARIO"})
        void orientacoes(String pergunta, Intencao esperada) {
            assertThat(i(pergunta).intencao()).isEqualTo(esperada);
        }
    }

    @Nested
    @DisplayName("Conversa e perguntas não reconhecidas")
    class Conversa {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
                "Olá                          | SAUDACAO",
                "Bom dia, Doki!               | SAUDACAO",
                "Obrigado!                    | AGRADECIMENTO",
                "valeu doki                   | AGRADECIMENTO",
                "O que você sabe fazer?       | AJUDA",
                "ajuda                        | AJUDA",
                "como você pode me ajudar?    | AJUDA",
                "oi, tudo bem?                | SAUDACAO",
                "Oi Doki, quais venceram?     | DOCUMENTOS_VENCIDOS",
                "obg!                         | AGRADECIMENTO"})
        void conversa(String pergunta, Intencao esperada) {
            assertThat(i(pergunta).intencao()).isEqualTo(esperada);
        }

        @ParameterizedTest(name = "\"{0}\" -> DESCONHECIDA")
        @ValueSource(strings = {"Qual a previsão do tempo amanhã?", "Me conte uma piada", "   ???  ", "asdfgh",
                "Quem ganhou o jogo ontem?", "Qual a capital da França?", "Quero pedir uma pizza"})
        @NullAndEmptySource
        void naoReconhecidas(String pergunta) {
            assertThat(i(pergunta).intencao()).isEqualTo(Intencao.DESCONHECIDA);
        }
    }
}
