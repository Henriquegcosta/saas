package br.com.vigentra.service.doki;

/**
 * O que o usuário quer saber. É o "contrato" entre a INTERPRETAÇÃO da pergunta
 * (hoje por regras; no futuro, por um modelo de IA) e as REGRAS que montam a resposta.
 * Um interpretador baseado em IA só precisa devolver uma destas intenções.
 */
public enum Intencao {

    // ---- consultas a dados reais (somente leitura) ----
    DOCUMENTOS_PROXIMOS("Documentos próximos do vencimento (opcionalmente em N dias)"),
    DOCUMENTOS_VENCIDOS("Documentos já vencidos"),
    TOTAL_DOCUMENTOS("Quantidade de documentos cadastrados"),
    CERTIFICADOS_ATENCAO("Certificados vencidos ou próximos do vencimento"),

    // ---- orientações de uso do sistema ----
    COMO_CADASTRAR_DOCUMENTO("Como cadastrar um documento"),
    COMO_CADASTRAR_CERTIFICADO("Como cadastrar um certificado"),
    COMO_ATUALIZAR_DOCUMENTO("Como atualizar/renovar um documento"),
    COMO_USAR_CALENDARIO("Como funciona o calendário de vencimentos"),
    ONDE_RELATORIOS("Onde ficam os relatórios"),
    COMO_CONVIDAR_USUARIO("Como convidar outro usuário"),

    // ---- conversa ----
    SAUDACAO("Cumprimento"),
    AGRADECIMENTO("Agradecimento"),
    AJUDA("O que o Doki sabe fazer"),
    DESCONHECIDA("Pergunta não reconhecida");

    private final String descricao;

    Intencao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() { return descricao; }
}
