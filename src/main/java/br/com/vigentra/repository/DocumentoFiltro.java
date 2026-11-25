package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.StatusDocumento;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Parâmetros de pesquisa/filtro de documentos (vinculados a partir da query string). */
public class DocumentoFiltro {

    private String q;
    private Long categoriaId;
    private StatusDocumento status;
    private Long responsavelId;
    private String empresaResponsavel;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate vencimentoDe;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate vencimentoAte;
    private String ordem = "vencimento";

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public StatusDocumento getStatus() { return status; }
    public void setStatus(StatusDocumento status) { this.status = status; }
    public Long getResponsavelId() { return responsavelId; }
    public void setResponsavelId(Long responsavelId) { this.responsavelId = responsavelId; }
    public String getEmpresaResponsavel() { return empresaResponsavel; }
    public void setEmpresaResponsavel(String v) { this.empresaResponsavel = v; }
    public LocalDate getVencimentoDe() { return vencimentoDe; }
    public void setVencimentoDe(LocalDate vencimentoDe) { this.vencimentoDe = vencimentoDe; }
    public LocalDate getVencimentoAte() { return vencimentoAte; }
    public void setVencimentoAte(LocalDate vencimentoAte) { this.vencimentoAte = vencimentoAte; }
    public String getOrdem() { return ordem; }
    public void setOrdem(String ordem) { this.ordem = ordem; }

    public boolean isAtivo() {
        return (q != null && !q.isBlank()) || categoriaId != null || status != null || responsavelId != null
                || (empresaResponsavel != null && !empresaResponsavel.isBlank())
                || vencimentoDe != null || vencimentoAte != null;
    }
}
