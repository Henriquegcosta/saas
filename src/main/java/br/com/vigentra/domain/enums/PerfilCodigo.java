package br.com.vigentra.domain.enums;

/**
 * Níveis de acesso. O "nivel" define hierarquia: um usuário só gerencia
 * usuários de nível inferior ao seu (exceto o Administrador, que gerencia todos).
 */
public enum PerfilCodigo {
    ADMIN("Administrador", 3, "Acesso completo ao ambiente da empresa."),
    GESTOR("Gestor", 2, "Gerencia documentos, relatórios e usuários conforme permissões."),
    USUARIO("Usuário", 1, "Consulta e gerencia os documentos autorizados.");

    private final String nome;
    private final int nivel;
    private final String descricao;

    PerfilCodigo(String nome, int nivel, String descricao) {
        this.nome = nome;
        this.nivel = nivel;
        this.descricao = descricao;
    }

    public String getNome() { return nome; }
    public int getNivel() { return nivel; }
    public String getDescricao() { return descricao; }
}
