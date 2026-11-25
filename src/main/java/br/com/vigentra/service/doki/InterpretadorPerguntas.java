package br.com.vigentra.service.doki;

/**
 * Transforma o texto digitado pelo usuário em uma {@link Interpretacao}.
 *
 * Implementação atual: {@link InterpretadorPorRegras} (palavras-chave, sem custo e sem serviços externos).
 * Evolução prevista: uma implementação que consulte um modelo de IA (linguagem natural, contexto
 * da conversa) e devolva a mesma {@link Interpretacao} - as regras de negócio e as consultas
 * ao banco continuam as mesmas, e o isolamento por empresa continua garantido no backend.
 */
public interface InterpretadorPerguntas {

    Interpretacao interpretar(String pergunta);
}
