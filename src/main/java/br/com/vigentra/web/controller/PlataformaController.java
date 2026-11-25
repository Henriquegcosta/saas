package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.NotificacaoService;
import br.com.vigentra.service.comunicacao.CanaisService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Painel GLOBAL de comunicação, exclusivo dos operadores da plataforma (APP_PLATFORM_ADMINS).
 * A infraestrutura de envio é centralizada no VIGENTRA, então o operador acompanha a saúde das
 * integrações e os envios de todas as empresas - com destinatários mascarados (minimização - LGPD)
 * e sem acesso aos documentos. Usuários comuns e administradores de empresas recebem 403.
 */
@Controller
@PreAuthorize("hasRole('PLATAFORMA')")
public class PlataformaController {

    private final HistoricoEnvioRepository historicoRepo;
    private final EmpresaRepository empresaRepo;
    private final NotificacaoService notificacaoService;
    private final CanaisService canaisService;
    private final AuditoriaService auditoria;

    public PlataformaController(HistoricoEnvioRepository historicoRepo, EmpresaRepository empresaRepo,
                                NotificacaoService notificacaoService, CanaisService canaisService, AuditoriaService auditoria) {
        this.historicoRepo = historicoRepo;
        this.empresaRepo = empresaRepo;
        this.notificacaoService = notificacaoService;
        this.canaisService = canaisService;
        this.auditoria = auditoria;
    }

    @GetMapping("/plataforma/comunicacao")
    public String painel(@RequestParam(required = false) Long empresaId, @RequestParam(required = false) StatusEnvio status,
                         @RequestParam(required = false) CanalEnvio canal, @RequestParam(required = false) TipoMensagem tipoMensagem,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("integracoes", canaisService.status());
        model.addAttribute("porCanal", notificacaoService.contagemPorCanal(null));
        model.addAttribute("pagina", historicoRepo.buscarPlataforma(empresaId, status, canal, tipoMensagem,
                PageRequest.of(Math.max(pagina, 0), 25, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("empresas", empresaRepo.findAll(Sort.by("nome")));
        model.addAttribute("empresaId", empresaId);
        model.addAttribute("status", status);
        model.addAttribute("canal", canal);
        model.addAttribute("tipoMensagem", tipoMensagem);
        model.addAttribute("statusLista", StatusEnvio.values());
        model.addAttribute("canais", CanalEnvio.values());
        model.addAttribute("tiposMensagem", TipoMensagem.values());
        if (pagina == 0) {
            auditoria.registrar(AcaoLog.ACESSO_PLATAFORMA, "Plataforma", null, "Consulta ao painel global de comunicação"
                    + (empresaId != null ? " (filtro: empresa " + empresaId + ")" : ""));
        }
        return "plataforma/comunicacao";
    }
}
