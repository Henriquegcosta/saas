package br.com.vigentra.service.comunicacao;

/** Estado de uma integração de canal, exibido nos painéis (nunca inclui credenciais). */
public enum EstadoIntegracao {
    ATIVA("Ativa", "success", "bi-check-circle"),
    MODO_DEV("Modo desenvolvimento", "info", "bi-code-slash"),
    NAO_CONFIGURADA("Não configurada", "warning", "bi-exclamation-triangle"),
    INDISPONIVEL("Indisponível nesta versão", "secondary", "bi-slash-circle");

    private final String label;
    private final String cor;
    private final String icone;

    EstadoIntegracao(String label, String cor, String icone) {
        this.label = label;
        this.cor = cor;
        this.icone = icone;
    }

    /** O canal pode ser usado pela rotina (ativo de verdade ou gerando em modo desenvolvimento)? */
    public boolean utilizavel() {
        return this == ATIVA || this == MODO_DEV;
    }

    public String getLabel() { return label; }
    public String getCor() { return cor; }
    public String getIcone() { return icone; }
}
