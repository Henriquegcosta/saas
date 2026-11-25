package br.com.vigentra.repository;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.model.HistoricoEnvio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HistoricoEnvioRepository extends JpaRepository<HistoricoEnvio, Long> {

    /** Verificação de duplicidade por canal (a constraint única no banco é a segunda barreira). */
    @Query("select count(h) > 0 from HistoricoEnvio h where h.documento.id = :documentoId and h.periodo = :periodo " +
           "and h.dataVencimentoRef = :vencimento and h.canal = :canal and lower(h.destinatario) = lower(:destinatario)")
    boolean jaRegistrado(@Param("documentoId") Long documentoId, @Param("periodo") String periodo,
                         @Param("vencimento") LocalDate vencimento, @Param("canal") CanalEnvio canal,
                         @Param("destinatario") String destinatario);

    /** Histórico de UMA empresa (área do cliente). */
    @Query("select h from HistoricoEnvio h where h.empresa.id = :empresaId " +
           "and (:status is null or h.status = :status) and (:tipo is null or h.tipoAlerta = :tipo) " +
           "and (:canal is null or h.canal = :canal) and (:tipoMensagem is null or h.tipoMensagem = :tipoMensagem) " +
           "and (:q is null or lower(h.documentoNome) like :q or lower(h.destinatario) like :q)")
    Page<HistoricoEnvio> buscar(@Param("empresaId") Long empresaId, @Param("status") StatusEnvio status,
                                @Param("tipo") TipoAlerta tipo, @Param("canal") CanalEnvio canal,
                                @Param("tipoMensagem") TipoMensagem tipoMensagem, @Param("q") String q, Pageable pageable);

    /** Histórico de TODAS as empresas - exclusivo do painel da plataforma (operadores do VIGENTRA). */
    @Query(value = "select h from HistoricoEnvio h join fetch h.empresa e where (:empresaId is null or e.id = :empresaId) " +
           "and (:status is null or h.status = :status) and (:canal is null or h.canal = :canal) " +
           "and (:tipoMensagem is null or h.tipoMensagem = :tipoMensagem)",
           countQuery = "select count(h) from HistoricoEnvio h where (:empresaId is null or h.empresa.id = :empresaId) " +
           "and (:status is null or h.status = :status) and (:canal is null or h.canal = :canal) " +
           "and (:tipoMensagem is null or h.tipoMensagem = :tipoMensagem)")
    Page<HistoricoEnvio> buscarPlataforma(@Param("empresaId") Long empresaId, @Param("status") StatusEnvio status,
                                          @Param("canal") CanalEnvio canal, @Param("tipoMensagem") TipoMensagem tipoMensagem,
                                          Pageable pageable);

    @Query("select h from HistoricoEnvio h where h.empresa.id = :empresaId order by h.createdAt desc")
    List<HistoricoEnvio> findRecentes(@Param("empresaId") Long empresaId, Pageable pageable);

    @Query("select h from HistoricoEnvio h join fetch h.empresa left join fetch h.documento " +
           "where h.status = br.com.vigentra.domain.enums.StatusEnvio.FALHA " +
           "and h.tipoMensagem = br.com.vigentra.domain.enums.TipoMensagem.ALERTA_VENCIMENTO " +
           "and h.tentativas < :maxTentativas and (h.proximaTentativa is null or h.proximaTentativa <= :agora)")
    List<HistoricoEnvio> findParaRetentativa(@Param("maxTentativas") int maxTentativas, @Param("agora") LocalDateTime agora);

    @Query("select h from HistoricoEnvio h left join fetch h.documento where h.id = :id and h.empresa.id = :empresaId")
    Optional<HistoricoEnvio> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);

    Optional<HistoricoEnvio> findFirstByIdExterno(String idExterno);

    @Query("select h.status, count(h) from HistoricoEnvio h where h.empresa.id = :empresaId group by h.status")
    List<Object[]> contarPorStatus(@Param("empresaId") Long empresaId);

    @Query("select h.canal, h.status, count(h) from HistoricoEnvio h where (:empresaId is null or h.empresa.id = :empresaId) " +
           "group by h.canal, h.status")
    List<Object[]> contarPorCanalEStatus(@Param("empresaId") Long empresaId);

    @Query("select h from HistoricoEnvio h where h.documento.id = :documentoId and h.empresa.id = :empresaId order by h.createdAt desc")
    List<HistoricoEnvio> findByDocumento(@Param("documentoId") Long documentoId, @Param("empresaId") Long empresaId);

    @Modifying
    @Query("update HistoricoEnvio h set h.documento = null where h.documento.id = :documentoId")
    void desvincularDocumento(@Param("documentoId") Long documentoId);
}
