package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.Configuracao;
import br.com.vigentra.domain.model.RegraCanalAlerta;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.RegraCanalAlertaRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.alerta.PeriodoAlerta;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canais usados em cada prazo da régua. Padrão sugerido:
 *   30 e 15 dias antes      -> e-mail
 *   7 dias, no dia e após   -> e-mail + WhatsApp
 * Prazos novos (ex.: 60 dias) recebem o padrão automaticamente.
 */
@Service
public class RegraCanalService {

    private final RegraCanalAlertaRepository repo;
    private final EmpresaRepository empresaRepo;
    private final ConfiguracaoService configuracaoService;
    private final AuditoriaService auditoria;

    public RegraCanalService(RegraCanalAlertaRepository repo, EmpresaRepository empresaRepo,
                             ConfiguracaoService configuracaoService, AuditoriaService auditoria) {
        this.repo = repo;
        this.empresaRepo = empresaRepo;
        this.configuracaoService = configuracaoService;
        this.auditoria = auditoria;
    }

    /** Chave da regra para um período calculado (POS1, POS2... usam a mesma regra "POS"). */
    public static String chave(PeriodoAlerta p) {
        return p.codigo().startsWith("POS") ? "POS" : p.codigo();
    }

    /** Regras da empresa para todos os prazos configurados (cria as que faltarem com o padrão). */
    public List<RegraCanalAlerta> regras(Long empresaId) {
        Configuracao cfg = configuracaoService.daEmpresa(empresaId);
        Map<String, RegraCanalAlerta> existentes = repo.findByEmpresa(empresaId).stream()
                .collect(Collectors.toMap(RegraCanalAlerta::getPeriodo, r -> r, (a, b) -> a, LinkedHashMap::new));
        List<String> periodos = new ArrayList<>();
        cfg.getDiasAlertaLista().forEach(d -> periodos.add("D" + d));
        periodos.add("POS");
        List<RegraCanalAlerta> lista = new ArrayList<>();
        for (String p : periodos) {
            RegraCanalAlerta r = existentes.get(p);
            if (r == null) r = criarPadrao(empresaId, p);
            lista.add(r);
        }
        lista.sort(Comparator.comparingInt(RegraCanalAlerta::getOrdem).reversed());
        return lista;
    }

    public Map<String, RegraCanalAlerta> mapa(Long empresaId) {
        return regras(empresaId).stream().collect(Collectors.toMap(RegraCanalAlerta::getPeriodo, r -> r));
    }

    @Transactional
    public void salvar(Map<String, Set<CanalEnvio>> canaisPorPeriodo) {
        SecurityUtils.exigir(PermissaoCodigo.SETTINGS_MANAGE);
        Long empresaId = SecurityUtils.empresaId();
        List<String> resumo = new ArrayList<>();
        for (RegraCanalAlerta r : regras(empresaId)) {
            Set<CanalEnvio> canais = canaisPorPeriodo.getOrDefault(r.getPeriodo(), Set.of());
            r.setEmail(canais.contains(CanalEnvio.EMAIL));
            r.setWhatsapp(canais.contains(CanalEnvio.WHATSAPP));
            r.setSms(canais.contains(CanalEnvio.SMS));
            repo.save(r);
            resumo.add(r.getPeriodoLabel() + ": " + (canais.isEmpty() ? "nenhum"
                    : canais.stream().map(CanalEnvio::getLabel).sorted().collect(Collectors.joining(" + "))));
        }
        auditoria.registrar(AcaoLog.CANAIS_ALTERADOS, "Configuracao", null, "Canais por prazo: " + String.join("; ", resumo));
    }

    private RegraCanalAlerta criarPadrao(Long empresaId, String periodo) {
        boolean urgente = "POS".equals(periodo) || Integer.parseInt(periodo.substring(1)) <= 7;
        RegraCanalAlerta r = new RegraCanalAlerta(empresaRepo.getReferenceById(empresaId), periodo, true, urgente, false);
        try {
            return repo.save(r);
        } catch (DataIntegrityViolationException e) {
            // criada em paralelo por outra execução
            return repo.findByEmpresa(empresaId).stream().filter(x -> x.getPeriodo().equals(periodo)).findFirst().orElse(r);
        }
    }
}
