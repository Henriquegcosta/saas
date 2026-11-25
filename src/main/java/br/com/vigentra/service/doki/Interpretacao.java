package br.com.vigentra.service.doki;

/**
 * Resultado da interpretação de uma pergunta.
 *
 * @param intencao o que o usuário quer
 * @param dias     prazo citado na pergunta ("próximos 7 dias", "nesta semana"...); null se não houver
 */
public record Interpretacao(Intencao intencao, Integer dias) {

    public static Interpretacao de(Intencao intencao) {
        return new Interpretacao(intencao, null);
    }
}
