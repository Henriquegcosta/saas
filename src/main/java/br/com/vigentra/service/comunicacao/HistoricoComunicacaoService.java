package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.domain.model.HistoricoEnvio;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Registra no histórico as mensagens que não são alertas de documento (conta, testes, avisos). */
@Service
public class HistoricoComunicacaoService {

    private final HistoricoEnvioRepository repo;
    private final EmpresaRepository empresaRepo;
    private final Clock clock;

    public HistoricoComunicacaoService(HistoricoEnvioRepository repo, EmpresaRepository empresaRepo, Clock clock) {
        this.repo = repo;
        this.empresaRepo = empresaRepo;
        this.clock = clock;
    }

    /** REQUIRES_NEW: pode ser chamado após o commit de outra transação ou em outra thread. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HistoricoEnvio registrar(Long empresaId, CanalEnvio canal, TipoMensagem tipo, String referencia,
                                    String destinatario, String assunto, ResultadoEnvio r) {
        HistoricoEnvio h = new HistoricoEnvio();
        h.setEmpresa(empresaRepo.getReferenceById(empresaId));
        h.setCanal(canal);
        h.setTipoMensagem(tipo);
        h.setTipoAlerta(TipoAlerta.SISTEMA);
        h.setPeriodo(tipo.getCodigo());
        h.setDocumentoNome(referencia);
        h.setDestinatario(destinatario);
        h.setAssunto(assunto.length() > 255 ? assunto.substring(0, 255) : assunto);
        h.setStatus(r.status());
        h.setProvedor(r.provedor());
        h.setIdExterno(r.idExterno());
        h.setUltimoErro(r.erro());
        h.setTentativas(1);
        h.setEnviadoEm(r.sucesso() ? LocalDateTime.now(clock) : null);
        return repo.save(h);
    }
}
