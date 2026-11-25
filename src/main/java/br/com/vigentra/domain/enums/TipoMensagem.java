package br.com.vigentra.domain.enums;

/** Finalidade da mensagem registrada no histórico de envios. */
public enum TipoMensagem {
    ALERTA_VENCIMENTO("Alerta de vencimento", "ALERTA", true),
    CONFIRMACAO_EMAIL("Confirmação de e-mail", "CONFIRMACAO", false),
    ALTERACAO_EMAIL("Confirmação de novo e-mail", "ALTERACAO_EMAIL", false),
    BOAS_VINDAS("Boas-vindas", "BOAS_VINDAS", false),
    RECUPERACAO_SENHA("Recuperação de senha", "RECUPERACAO", false),
    SENHA_ALTERADA("Aviso de senha alterada", "SENHA_ALTERADA", false),
    CONVITE("Convite para empresa", "CONVITE", false),
    TESTE("Mensagem de teste", "TESTE", false),
    ALERTA_ADMINISTRATIVO("Alerta administrativo", "ADMIN", false);

    private final String label;
    /** Código curto gravado na coluna "periodo" para mensagens que não são alertas. */
    private final String codigo;
    /** Somente alertas são reenviados automaticamente: os demais contêm links de uso único. */
    private final boolean retentavel;

    TipoMensagem(String label, String codigo, boolean retentavel) {
        this.label = label;
        this.codigo = codigo;
        this.retentavel = retentavel;
    }

    public String getLabel() { return label; }
    public String getCodigo() { return codigo; }
    public boolean isRetentavel() { return retentavel; }
}
