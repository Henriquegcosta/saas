package br.com.vigentra.web.controller;

import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.comunicacao.LinkPreferenciasService.Alvo;
import br.com.vigentra.service.comunicacao.PreferenciasService;
import br.com.vigentra.service.comunicacao.PreferenciasService.Titular;
import br.com.vigentra.service.comunicacao.whatsapp.WhatsAppService;
import br.com.vigentra.service.comunicacao.sms.SmsService;
import br.com.vigentra.web.dto.PreferenciasForm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gerenciamento de preferências e descadastro SEM login, por link assinado (HMAC) enviado
 * no rodapé dos alertas. Atende responsáveis que não têm conta no sistema (LGPD - art. 18).
 */
@Controller
public class PreferenciasPublicController {

    private final PreferenciasService preferencias;
    private final WhatsAppService whatsAppService;
    private final SmsService smsService;

    public PreferenciasPublicController(PreferenciasService preferencias, WhatsAppService whatsAppService, SmsService smsService) {
        this.preferencias = preferencias;
        this.whatsAppService = whatsAppService;
        this.smsService = smsService;
    }

    @GetMapping("/preferencias")
    public String pagina(@RequestParam Alvo a, @RequestParam Long id, @RequestParam String t, Model model) {
        Titular titular = preferencias.titular(a, id, t);
        return pagina(titular, PreferenciasForm.de(titular.preferencias(), titular.celular()), a, id, t, model);
    }

    @PostMapping("/preferencias")
    public String salvar(@RequestParam Alvo a, @RequestParam Long id, @RequestParam String t,
                         @ModelAttribute("form") PreferenciasForm form, Model model, RedirectAttributes ra) {
        try {
            preferencias.salvarPeloLink(a, id, t, form);
        } catch (NegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina(preferencias.titular(a, id, t), form, a, id, t, model);
        }
        ra.addFlashAttribute("sucesso", "Preferências salvas.");
        ra.addAttribute("a", a);
        ra.addAttribute("id", id);
        ra.addAttribute("t", t);
        return "redirect:/preferencias";
    }

    /** Link "descadastrar" do rodapé: mostra a confirmação (um robô que abre o link não descadastra). */
    @GetMapping("/preferencias/descadastrar")
    public String confirmarDescadastro(@RequestParam Alvo a, @RequestParam Long id, @RequestParam String t, Model model) {
        model.addAttribute("titular", preferencias.titular(a, id, t));
        model.addAttribute("a", a);
        model.addAttribute("id", id);
        model.addAttribute("t", t);
        return "public/descadastro";
    }

    /**
     * Descadastro efetivo. Atende o botão da página e o "One-Click" (RFC 8058) dos clientes de e-mail,
     * que enviam POST com corpo "List-Unsubscribe=One-Click" sem sessão nem token CSRF.
     */
    @PostMapping("/preferencias/descadastrar")
    public Object descadastrar(@RequestParam Alvo a, @RequestParam Long id, @RequestParam String t,
                               HttpServletRequest req, Model model) {
        Titular titular = preferencias.descadastrarEmail(a, id, t);
        if ("One-Click".equals(req.getParameter("List-Unsubscribe"))) {
            return ResponseEntity.ok("Descadastrado.");
        }
        model.addAttribute("titular", titular);
        model.addAttribute("concluido", true);
        model.addAttribute("linkPreferencias", "/preferencias?a=" + a + "&id=" + id + "&t=" + t);
        return "public/descadastro";
    }

    private String pagina(Titular titular, PreferenciasForm form, Alvo a, Long id, String t, Model model) {
        model.addAttribute("titular", titular);
        model.addAttribute("form", form);
        model.addAttribute("a", a);
        model.addAttribute("id", id);
        model.addAttribute("t", t);
        model.addAttribute("whatsappStatus", whatsAppService.status());
        model.addAttribute("smsStatus", smsService.status());
        return "public/preferencias";
    }
}
