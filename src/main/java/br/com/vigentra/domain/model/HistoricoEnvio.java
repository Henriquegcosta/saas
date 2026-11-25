package br.com.vigentra.domain.model;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.enums.TipoAlerta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de cada mensagem enviada (qualquer canal). A restrição única
 * (documento, período, vencimento de referência, canal, destinatário) garante,
 * no próprio banco, que o mesmo alerta não seja enviado duas vezes.
 */
@Entity
@Table(name = "historico_envios",
        uniqueConstraints = @UniqueConstraint(name = "uk_envio_canal",
                columnNames = {"documento_id", "periodo", "data_vencimento_ref", "canal", "destinatario"}),
        indexes = @Index(name = "idx_envios_id_externo", columnList = "id_externo"))
public class HistoricoEnvio extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    /** Cópia do nome do documento (mantém o histórico legível mesmo após exclusão). */
    @Column(name = "documento_nome", nullable = false, length = 150)
    private String documentoNome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CanalEnvio canal = CanalEnvio.EMAIL;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tipo_mensagem", nullable = false, length = 30)
    private TipoMensagem tipoMensagem = TipoMensagem.ALERTA_VENCIMENTO;

    /** Provedor que processou o envio (brevo, smtp, whatsapp-cloud, dev...). */
    @Column(length = 30)
    private String provedor;

    /** Identificador da mensagem no provedor (usado pelos webhooks de status). */
    @Column(name = "id_externo", length = 150)
    private String idExterno;

    /** Indica que os administradores já foram avisados da falha definitiva deste envio. */
    @Column(name = "falha_notificada", nullable = false)
    private boolean falhaNotificada = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tipo_alerta", nullable = false, length = 20)
    private TipoAlerta tipoAlerta;

    /** Período do alerta: D30, D15, D7, D0, POS1, POS2... ou TESTE. */
    @Column(nullable = false, length = 20)
    private String periodo;

    @Column(name = "data_vencimento_ref")
    private LocalDate dataVencimentoRef;

    @Column(nullable = false, length = 150)
    private String destinatario;

    @Column(nullable = false, length = 255)
    private String assunto;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StatusEnvio status = StatusEnvio.PENDENTE;

    @Column(nullable = false)
    private Integer tentativas = 0;

    @Column(name = "ultimo_erro", length = 1000)
    private String ultimoErro;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @Column(name = "proxima_tentativa")
    private LocalDateTime proximaTentativa;

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public Documento getDocumento() { return documento; }
    public void setDocumento(Documento documento) { this.documento = documento; }
    public String getDocumentoNome() { return documentoNome; }
    public void setDocumentoNome(String documentoNome) { this.documentoNome = documentoNome; }
    public TipoAlerta getTipoAlerta() { return tipoAlerta; }
    public void setTipoAlerta(TipoAlerta tipoAlerta) { this.tipoAlerta = tipoAlerta; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public LocalDate getDataVencimentoRef() { return dataVencimentoRef; }
    public void setDataVencimentoRef(LocalDate d) { this.dataVencimentoRef = d; }
    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    public String getAssunto() { return assunto; }
    public void setAssunto(String assunto) { this.assunto = assunto; }
    public StatusEnvio getStatus() { return status; }
    public void setStatus(StatusEnvio status) { this.status = status; }
    public Integer getTentativas() { return tentativas; }
    public void setTentativas(Integer tentativas) { this.tentativas = tentativas; }
    public String getUltimoErro() { return ultimoErro; }
    public void setUltimoErro(String ultimoErro) { this.ultimoErro = ultimoErro; }
    public LocalDateTime getEnviadoEm() { return enviadoEm; }
    public void setEnviadoEm(LocalDateTime enviadoEm) { this.enviadoEm = enviadoEm; }
    public LocalDateTime getProximaTentativa() { return proximaTentativa; }
    public void setProximaTentativa(LocalDateTime p) { this.proximaTentativa = p; }

    /** Descrição amigável do período (ex.: "15 dias antes"). */
    public CanalEnvio getCanal() { return canal; }
    public void setCanal(CanalEnvio canal) { this.canal = canal; }
    public TipoMensagem getTipoMensagem() { return tipoMensagem; }
    public void setTipoMensagem(TipoMensagem t) { this.tipoMensagem = t; }
    public String getProvedor() { return provedor; }
    public void setProvedor(String provedor) { this.provedor = provedor; }
    public String getIdExterno() { return idExterno; }
    public void setIdExterno(String idExterno) { this.idExterno = idExterno; }
    public boolean isFalhaNotificada() { return falhaNotificada; }
    public void setFalhaNotificada(boolean v) { this.falhaNotificada = v; }

    public String getPeriodoLabel() {
        if (tipoMensagem != null && tipoMensagem != TipoMensagem.ALERTA_VENCIMENTO) return tipoMensagem.getLabel();
        if (periodo == null) return "";
        if (periodo.equals("D0")) return "No dia do vencimento";
        if (periodo.equals("TESTE")) return "E-mail de teste";
        if (periodo.startsWith("D")) return periodo.substring(1) + " dias antes";
        if (periodo.startsWith("POS")) return "Após o vencimento (" + periodo.substring(3) + "º aviso)";
        return periodo;
    }
}
