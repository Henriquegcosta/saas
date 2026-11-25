package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.model.Configuracao;
import br.com.vigentra.domain.model.Empresa;
import br.com.vigentra.repository.ConfiguracaoRepository;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.web.dto.AlertasForm;
import br.com.vigentra.web.dto.EmailConfigForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Configurações por empresa (alertas, e-mail, preferências). */
@Service
public class ConfiguracaoService {

    private final ConfiguracaoRepository repo;
    private final EmpresaRepository empresaRepo;
    private final AuditoriaService auditoria;

    public ConfiguracaoService(ConfiguracaoRepository repo, EmpresaRepository empresaRepo, AuditoriaService auditoria) {
        this.repo = repo;
        this.empresaRepo = empresaRepo;
        this.auditoria = auditoria;
    }

    /** Busca a configuração da empresa (cria a padrão, se ainda não existir). */
    @Transactional
    public Configuracao daEmpresa(Long empresaId) {
        return repo.findByEmpresaId(empresaId).orElseGet(() -> {
            Configuracao c = new Configuracao();
            c.setEmpresa(empresaRepo.getReferenceById(empresaId));
            return repo.save(c);
        });
    }

    @Transactional
    public Configuracao atual() {
        return daEmpresa(SecurityUtils.empresaId());
    }

    @Transactional
    public void criarPadrao(Empresa empresa) {
        Configuracao c = new Configuracao();
        c.setEmpresa(empresa);
        c.setEmailRemetenteNome("VIGENTRA - " + empresa.getNome());
        repo.save(c);
    }

    @Transactional
    public void salvarAlertas(AlertasForm f) {
        Configuracao c = atual();
        String dias = Arrays.stream(f.getDiasAlerta().split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .peek(s -> {
                    if (!s.matches("\\d{1,3}") || Integer.parseInt(s) > 365)
                        throw new NegocioException("Dias de alerta inválidos: use números de 0 a 365 separados por vírgula.");
                })
                .map(Integer::parseInt).distinct().sorted((a, b) -> b - a)
                .map(String::valueOf).collect(Collectors.joining(","));
        if (dias.isEmpty()) throw new NegocioException("Informe ao menos um dia de alerta.");
        c.setDiasAlerta(dias);
        c.setAlertarAposVencimento(f.isAlertarAposVencimento());
        c.setIntervaloPosVencimento(f.getIntervaloPosVencimento());
        c.setMaxAlertasPosVencimento(f.getMaxAlertasPosVencimento());
        c.setDiasProximoVencimento(f.getDiasProximoVencimento());
        c.setNotificarResponsavel(f.isNotificarResponsavel());
        c.setNotificarGestores(f.isNotificarGestores());
        repo.save(c);
        auditoria.registrar(AcaoLog.CONFIGURACAO_ALTERADA, "Configuracao", c.getId(),
                "Configuração de alertas alterada (dias: " + dias + ", pós-vencimento: "
                        + (f.isAlertarAposVencimento() ? "a cada " + f.getIntervaloPosVencimento() + " dias" : "desativado") + ")");
    }

    @Transactional
    public void salvarEmail(EmailConfigForm f) {
        Configuracao c = atual();
        c.setEmailRemetenteNome(f.getEmailRemetenteNome());
        c.setEmailResposta(blankToNull(f.getEmailResposta()));
        c.setEmailCopia(blankToNull(f.getEmailCopia()));
        c.setEmailAssinatura(blankToNull(f.getEmailAssinatura()));
        repo.save(c);
        auditoria.registrar(AcaoLog.CONFIGURACAO_ALTERADA, "Configuracao", c.getId(), "Configuração de e-mail alterada");
    }

    @Transactional
    public void salvarPreferenciasSistema(int itensPorPagina) {
        if (itensPorPagina < 5 || itensPorPagina > 100) throw new NegocioException("Itens por página deve estar entre 5 e 100.");
        Configuracao c = atual();
        c.setItensPorPagina(itensPorPagina);
        repo.save(c);
        auditoria.registrar(AcaoLog.CONFIGURACAO_ALTERADA, "Configuracao", c.getId(),
                "Preferências do sistema alteradas (itens por página: " + itensPorPagina + ")");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
