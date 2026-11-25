package br.com.vigentra.domain.enums;

/** Ações registradas no log de atividades (auditoria). */
public enum AcaoLog {
    LOGIN("Login", "bi-box-arrow-in-right", "primary"),
    LOGIN_FALHA("Falha de login", "bi-shield-exclamation", "danger"),
    LOGOUT("Logout", "bi-box-arrow-right", "secondary"),
    EMPRESA_CADASTRADA("Empresa cadastrada", "bi-building-add", "success"),
    EMPRESA_ALTERADA("Dados da empresa alterados", "bi-building-gear", "primary"),
    DOCUMENTO_CRIADO("Documento cadastrado", "bi-file-earmark-plus", "success"),
    DOCUMENTO_ALTERADO("Documento alterado", "bi-pencil-square", "primary"),
    DOCUMENTO_EXCLUIDO("Documento excluído", "bi-trash", "danger"),
    USUARIO_CRIADO("Usuário cadastrado", "bi-person-plus", "success"),
    USUARIO_ALTERADO("Usuário alterado", "bi-person-gear", "primary"),
    USUARIO_DESATIVADO("Usuário desativado", "bi-person-slash", "warning"),
    USUARIO_ATIVADO("Usuário reativado", "bi-person-check", "success"),
    USUARIO_EXCLUIDO("Usuário excluído", "bi-person-x", "danger"),
    SENHA_ALTERADA("Senha alterada", "bi-key", "primary"),
    EMAIL_CONFIRMADO("E-mail confirmado", "bi-envelope-check", "success"),
    CONVITE_ENVIADO("Convite enviado", "bi-envelope-plus", "primary"),
    CONVITE_ACEITO("Convite aceito", "bi-person-check", "success"),
    CONVITE_CANCELADO("Convite cancelado", "bi-envelope-x", "warning"),
    PREFERENCIAS_ALTERADAS("Preferências de comunicação alteradas", "bi-sliders", "primary"),
    CONSENTIMENTO_REVOGADO("Consentimento revogado (descadastro)", "bi-bell-slash", "warning"),
    CANAIS_ALTERADOS("Canais de alerta alterados", "bi-broadcast", "primary"),
    ACESSO_PLATAFORMA("Acesso ao painel da plataforma", "bi-shield-lock", "secondary"),
    SENHA_RECUPERACAO("Recuperação de senha", "bi-key", "warning"),
    PERMISSOES_ALTERADAS("Permissões alteradas", "bi-shield-lock", "warning"),
    RESPONSAVEL_ALTERADO("Responsável alterado", "bi-person-vcard", "primary"),
    CATEGORIA_ALTERADA("Categoria alterada", "bi-tags", "primary"),
    CONFIGURACAO_ALTERADA("Configuração alterada", "bi-gear", "primary"),
    NOTIFICACAO_ENVIADA("Notificação enviada", "bi-envelope-check", "success"),
    EMAIL_FALHA("Falha no envio de e-mail", "bi-envelope-exclamation", "danger"),
    ROTINA_ALERTAS("Rotina de alertas executada", "bi-clock-history", "secondary"),
    RELATORIO_EXPORTADO("Relatório exportado", "bi-file-earmark-arrow-down", "primary"),
    DADOS_EXPORTADOS("Exportação de dados pessoais (LGPD)", "bi-shield-check", "primary");

    private final String label;
    private final String icone;
    private final String cor;

    AcaoLog(String label, String icone, String cor) {
        this.label = label;
        this.icone = icone;
        this.cor = cor;
    }

    public String getLabel() { return label; }
    public String getIcone() { return icone; }
    public String getCor() { return cor; }
}
