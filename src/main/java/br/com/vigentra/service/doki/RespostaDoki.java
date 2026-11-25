package br.com.vigentra.service.doki;

import java.util.List;

/**
 * Resposta do Doki enviada ao navegador (JSON). Contém apenas TEXTO e dados mínimos:
 * o frontend exibe tudo com textContent (nunca como HTML), evitando injeção de código.
 *
 * @param intencao   intenção reconhecida (útil para depuração e para a futura camada de IA)
 * @param texto      mensagem principal
 * @param passos     passo a passo (orientações), na ordem
 * @param documentos documentos encontrados (já filtrados pela empresa e pela visibilidade do usuário)
 * @param total      total de documentos que atendem à consulta (pode ser maior que a lista exibida)
 * @param links      atalhos para telas do sistema (somente telas que o usuário pode acessar)
 * @param sugestoes  próximas perguntas sugeridas
 */
public record RespostaDoki(String intencao, String texto, List<String> passos, List<DocumentoResumo> documentos,
                           Long total, List<Link> links, List<String> sugestoes) {

    /** Dados mínimos de um documento: sem anexos, observações, e-mails ou telefones. */
    public record DocumentoResumo(Long id, String nome, String categoria, String vencimento, String prazo,
                                  String status, String statusCor, String url) {}

    public record Link(String rotulo, String url) {}
}
