package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoMensagem;
import br.com.vigentra.service.comunicacao.CanaisService;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.model.Notificacao;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.NotificacaoService;
import br.com.vigentra.service.alerta.AlertaService;
import br.com.vigentra.service.alerta.ResultadoRotina;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Central de notificações: alertas do usuário e histórico de envios de e-mail da empresa. */
@Controller
@RequestMapping("/app/notificacoes")
public class NotificacaoController {

    private final NotificacaoService service;
    private final AlertaService alertaService;
    private final ConfiguracaoService configuracaoService;
    private final CanaisService canaisService;

    public NotificacaoController(NotificacaoService service, AlertaService alertaService, ConfiguracaoService configuracaoService,
                                 CanaisService canaisService) {
        this.service = service;
        this.alertaService = alertaService;
        this.configuracaoService = configuracaoService;
        this.canaisService = canaisService;
    }

    @GetMapping
    public String minhas(@RequestParam(required = false) Boolean lida, @RequestParam(required = false) TipoAlerta tipo,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("aba", "minhas");
        model.addAttribute("pagina", service.minhas(lida, tipo, pagina, configuracaoService.atual().getItensPorPagina()));
        model.addAttribute("lida", lida);
        model.addAttribute("tipo", tipo);
        model.addAttribute("tipos", new TipoAlerta[]{TipoAlerta.ANTECEDENCIA, TipoAlerta.VENCE_HOJE, TipoAlerta.VENCIDO});
        return "app/notificacoes";
    }

    @GetMapping("/envios")
    @PreAuthorize("hasAnyAuthority('NOTIF_MANAGE','DOC_VIEW_ALL')")
    public String envios(@RequestParam(required = false) StatusEnvio status, @RequestParam(required = false) TipoAlerta tipo,
                         @RequestParam(required = false) CanalEnvio canal, @RequestParam(required = false) TipoMensagem tipoMensagem,
                         @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("aba", "envios");
        model.addAttribute("pagina", service.historico(status, tipo, canal, tipoMensagem, q, pagina,
                configuracaoService.atual().getItensPorPagina()));
        model.addAttribute("canal", canal);
        model.addAttribute("tipoMensagem", tipoMensagem);
        model.addAttribute("canais", CanalEnvio.values());
        model.addAttribute("tiposMensagem", TipoMensagem.values());
        model.addAttribute("porCanal", service.contagemPorCanal());
        model.addAttribute("integracoes", canaisService.status());
        var contagem = new java.util.HashMap<String, Long>();
        service.contagemEnvios().forEach((s, n) -> contagem.put(s.name(), n));
        model.addAttribute("contagem", contagem);
        model.addAttribute("status", status);
        model.addAttribute("tipo", tipo);
        model.addAttribute("q", q);
        model.addAttribute("statusLista", StatusEnvio.values());
        model.addAttribute("tipos", new TipoAlerta[]{TipoAlerta.ANTECEDENCIA, TipoAlerta.VENCE_HOJE, TipoAlerta.VENCIDO, TipoAlerta.SISTEMA});
        return "app/notificacoes";
    }

    /** Clicar na notificação: marca como lida e abre o documento relacionado. */
    @GetMapping("/{id}/abrir")
    public String abrir(@PathVariable Long id) {
        Notificacao n = service.marcarLida(id);
        return n.getDocumento() != null ? "redirect:/app/documentos/" + n.getDocumento().getId() : "redirect:/app/notificacoes";
    }

    @PostMapping("/{id}/lida")
    public String marcarLida(@PathVariable Long id, RedirectAttributes ra) {
        service.marcarLida(id);
        ra.addFlashAttribute("sucesso", "Notificação marcada como lida.");
        return "redirect:/app/notificacoes";
    }

    @PostMapping("/marcar-todas")
    public String marcarTodas(RedirectAttributes ra) {
        int n = service.marcarTodasLidas();
        ra.addFlashAttribute("sucesso", n == 0 ? "Não há notificações pendentes." : n + " notificação(ões) marcada(s) como lida(s).");
        return "redirect:/app/notificacoes";
    }

    @PostMapping("/envios/{id}/reenviar")
    @PreAuthorize("hasAuthority('NOTIF_MANAGE')")
    public String reenviar(@PathVariable Long id, RedirectAttributes ra) {
        StatusEnvio st = alertaService.reenviarManual(id);
        if (st == StatusEnvio.FALHA) ra.addFlashAttribute("erro", "O reenvio falhou novamente. Verifique o erro registrado e a configuração SMTP.");
        else ra.addFlashAttribute("sucesso", "Reenvio concluído: " + st.getLabel() + ".");
        return "redirect:/app/notificacoes/envios";
    }

    @PostMapping("/executar")
    @PreAuthorize("hasAuthority('NOTIF_MANAGE')")
    public String executar(RedirectAttributes ra) {
        ResultadoRotina r = alertaService.executarManual();
        ra.addFlashAttribute(r.falhas() > 0 ? "erro" : "sucesso", "Verificação concluída: " + r.resumo() + ".");
        return "redirect:/app/notificacoes/envios";
    }
}
