package br.com.vigentra.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Permissões granulares do sistema. Cada empresa possui sua própria matriz
 * Perfil x Permissão (tabela perfil_permissoes), inicializada com os padrões abaixo.
 */
public enum PermissaoCodigo {
    DOC_VIEW_ALL("Documentos", "Visualizar todos os documentos da empresa"),
    DOC_CREATE("Documentos", "Cadastrar documentos"),
    DOC_EDIT("Documentos", "Editar documentos"),
    DOC_DELETE("Documentos", "Excluir documentos"),
    RESP_MANAGE("Responsáveis", "Gerenciar responsáveis pelos documentos"),
    CATEGORY_MANAGE("Documentos", "Gerenciar categorias de documentos"),
    REPORT_VIEW("Relatórios", "Consultar relatórios"),
    REPORT_EXPORT("Relatórios", "Exportar relatórios (PDF, Excel, CSV)"),
    NOTIF_MANAGE("Notificações", "Gerenciar envios (reenviar falhas, executar verificação)"),
    USER_MANAGE("Administração", "Gerenciar usuários"),
    AUDIT_VIEW("Administração", "Consultar log de atividades"),
    SETTINGS_MANAGE("Administração", "Configurar empresa, alertas e e-mail");

    private final String grupo;
    private final String descricao;

    PermissaoCodigo(String grupo, String descricao) {
        this.grupo = grupo;
        this.descricao = descricao;
    }

    public String getGrupo() { return grupo; }
    public String getDescricao() { return descricao; }

    /** Permissões padrão de cada perfil para novas empresas. */
    public static Set<PermissaoCodigo> padrao(PerfilCodigo perfil) {
        return switch (perfil) {
            case ADMIN -> EnumSet.allOf(PermissaoCodigo.class);
            case GESTOR -> EnumSet.complementOf(EnumSet.of(SETTINGS_MANAGE));
            case USUARIO -> EnumSet.of(DOC_CREATE, DOC_EDIT);
        };
    }
}
