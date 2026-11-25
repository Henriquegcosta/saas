package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Specifications (JPA Criteria API) para montar consultas dinâmicas de documentos.
 * Os valores são sempre passados como PARÂMETROS - nunca concatenados ao SQL -
 * o que elimina o risco de SQL Injection.
 */
public final class DocumentoSpecs {

    private DocumentoSpecs() {}

    /** Isolamento multi-tenant: SEMPRE aplicado. */
    public static Specification<Documento> daEmpresa(Long empresaId) {
        return (root, q, cb) -> cb.equal(root.get("empresa").get("id"), empresaId);
    }

    /** Usuário sem permissão DOC_VIEW_ALL só vê os documentos sob sua responsabilidade ou criados por ele. */
    public static Specification<Documento> visivelPara(Long usuarioId) {
        return (root, q, cb) -> {
            Join<Object, Object> resp = root.join("responsavel", JoinType.LEFT);
            Join<Object, Object> criador = root.join("criadoPor", JoinType.LEFT);
            return cb.or(cb.equal(resp.get("usuario").get("id"), usuarioId),
                         cb.equal(criador.get("id"), usuarioId));
        };
    }

    public static Specification<Documento> comStatus(StatusDocumento status, LocalDate hoje, int diasProximo) {
        if (status == null) return null;
        LocalDate limite = hoje.plusDays(diasProximo);
        return (root, q, cb) -> switch (status) {
            case SEM_VENCIMENTO -> cb.or(cb.isTrue(root.get("semVencimento")), cb.isNull(root.get("dataVencimento")));
            case VENCIDO -> cb.and(cb.isFalse(root.get("semVencimento")), cb.lessThan(root.get("dataVencimento"), hoje));
            case PROXIMO_VENCIMENTO -> cb.and(cb.isFalse(root.get("semVencimento")),
                    cb.between(root.get("dataVencimento"), hoje, limite));
            case REGULAR -> cb.and(cb.isFalse(root.get("semVencimento")), cb.greaterThan(root.get("dataVencimento"), limite));
        };
    }

    public static Specification<Documento> texto(String termo) {
        if (termo == null || termo.isBlank()) return null;
        String like = "%" + termo.trim().toLowerCase() + "%";
        return (root, q, cb) -> {
            Join<Object, Object> resp = root.join("responsavel", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("nome")), like),
                    cb.like(cb.lower(root.get("numero")), like),
                    cb.like(cb.lower(root.get("descricao")), like),
                    cb.like(cb.lower(root.get("empresaResponsavel")), like),
                    cb.like(cb.lower(resp.get("nome")), like));
        };
    }

    public static Specification<Documento> categoria(Long categoriaId) {
        if (categoriaId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId);
    }

    public static Specification<Documento> responsavel(Long responsavelId) {
        if (responsavelId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("responsavel").get("id"), responsavelId);
    }

    public static Specification<Documento> empresaResponsavel(String nome) {
        if (nome == null || nome.isBlank()) return null;
        String like = "%" + nome.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("empresaResponsavel")), like);
    }

    public static Specification<Documento> vencimentoEntre(LocalDate de, LocalDate ate) {
        if (de == null && ate == null) return null;
        return (root, q, cb) -> {
            if (de != null && ate != null) return cb.between(root.get("dataVencimento"), de, ate);
            if (de != null) return cb.greaterThanOrEqualTo(root.get("dataVencimento"), de);
            return cb.lessThanOrEqualTo(root.get("dataVencimento"), ate);
        };
    }

    public static Specification<Documento> criadoDesde(java.time.LocalDateTime desde) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), desde);
    }
}
