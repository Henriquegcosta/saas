package br.com.vigentra.web.controller;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.model.Configuracao;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.service.CategoriaService;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.EmpresaService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.PermissaoService;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.service.comunicacao.CanaisService;
import br.com.vigentra.service.comunicacao.PreferenciasService;
import br.com.vigentra.service.comunicacao.RegraCanalService;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.conta.ContaService;
import br.com.vigentra.web.dto.AlertasForm;
import br.com.vigentra.web.dto.AlterarSenhaForm;
import br.com.vigentra.web.dto.CategoriaForm;
import br.com.vigentra.web.dto.EmailConfigForm;
import br.com.vigentra.web.dto.EmpresaForm;
import br.com.vigentra.web.dto.PerfilUsuarioForm;
import br.com.vigentra.web.dto.PreferenciasForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configurações em abas: dados do usuário, senha, preferências de notificação, empresa, alertas,
 * canais de comunicação, e-mail, sistema, categorias e permissões.
 */
@Controller
@RequestMapping("/app/configuracoes")
public class ConfiguracaoController {

    private static final Set<String> ABAS = Set.of("perfil", "senha", "notificacoes", "empresa", "alertas", "canais", "email",
            "sistema", "categorias", "permissoes");

    private final ConfiguracaoService configuracaoService;
    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;
    private final CategoriaService categoriaService;
    private final PermissaoService permissaoService;
    private final CanaisService canaisService;
    private final RegraCanalService regraCanalService;
    private final PreferenciasService preferenciasService;
    private final ContaService contaService;
    private final EmailService emailService;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public ConfiguracaoController(ConfiguracaoService configuracaoService, EmpresaService empresaService,
                                  UsuarioService usuarioService, CategoriaService categoriaService,
                                  PermissaoService permissaoService, CanaisService canaisService,
                                  RegraCanalService regraCanalService, PreferenciasService preferenciasService,
                                  ContaService contaService, EmailService emailService, AppProperties props, ObjectMapper mapper) {
        this.configuracaoService = configuracaoService;
        this.empresaService = empresaService;
        this.usuarioService = usuarioService;
        this.categoriaService = categoriaService;
        this.permissaoService = permissaoService;
        this.canaisService = canaisService;
        this.regraCanalService = regraCanalService;
        this.preferenciasService = preferenciasService;
        this.contaService = contaService;
        this.emailService = emailService;
        this.props = props;
        this.mapper = mapper;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "perfil") String aba,
                        @RequestParam(required = false) Long editarCategoria, Model model) {
        if (editarCategoria != null) {
            var c = categoriaService.buscar(editarCategoria);
            CategoriaForm f = new CategoriaForm();
            f.setId(c.getId());
            f.setNome(c.getNome());
            f.setDescricao(c.getDescricao());
            f.setCor(c.getCor());
            f.setIcone(c.getIcone());
            model.addAttribute("categoriaForm", f);
        }
        return pagina(aba, model);
    }

    // ---------------------------------------------------------------- empresa
    @PostMapping("/empresa")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public String empresa(@Valid @ModelAttribute("empresaForm") EmpresaForm f, BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) return pagina("empresa", model);
        try {
            empresaService.atualizar(f);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return pagina("empresa", model);
        }
        return ok(ra, "empresa", "Dados da empresa atualizados.");
    }

    // ---------------------------------------------------------------- perfil e e-mail de login
    @PostMapping("/perfil")
    public String perfil(@Valid @ModelAttribute("perfilForm") PerfilUsuarioForm f, BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) return pagina("perfil", model);
        usuarioService.atualizarProprioPerfil(f);
        return ok(ra, "perfil", "Perfil atualizado. O novo nome aparecerá no cabeçalho no próximo login.");
    }

    @PostMapping("/email-login")
    public String alterarEmailLogin(@RequestParam String novoEmail, RedirectAttributes ra) {
        contaService.solicitarAlteracaoEmail(novoEmail);
        return ok(ra, "perfil", "Enviamos um link de confirmação para " + novoEmail.trim().toLowerCase()
                + ". Seu e-mail atual continua valendo até a confirmação." + modoDev());
    }

    @PostMapping("/email-login/cancelar")
    public String cancelarEmailLogin(RedirectAttributes ra) {
        contaService.cancelarAlteracaoEmail();
        return ok(ra, "perfil", "Troca de e-mail cancelada.");
    }

    // ---------------------------------------------------------------- senha
    @PostMapping("/senha")
    public String senha(@Valid @ModelAttribute("senhaForm") AlterarSenhaForm f, BindingResult br, Model model, RedirectAttributes ra) {
        if (f.getNovaSenha() != null && !f.getNovaSenha().equals(f.getConfirmacao())) {
            br.rejectValue("confirmacao", "diferente", "A confirmação não confere com a nova senha.");
        }
        if (br.hasErrors()) return pagina("senha", model);
        try {
            usuarioService.alterarPropriaSenha(f);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return pagina("senha", model);
        }
        return ok(ra, "senha", "Senha alterada com sucesso. Enviamos um aviso de segurança para o seu e-mail.");
    }

    // ---------------------------------------------------------------- preferências de notificação (do próprio usuário)
    @PostMapping("/notificacoes")
    public String notificacoes(@ModelAttribute("prefForm") PreferenciasForm f, Model model, RedirectAttributes ra) {
        try {
            preferenciasService.salvarMinhas(f);
        } catch (NegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina("notificacoes", model);
        }
        return ok(ra, "notificacoes", "Preferências de notificação salvas.");
    }

    // ---------------------------------------------------------------- alertas
    @PostMapping("/alertas")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public String alertas(@Valid @ModelAttribute("alertasForm") AlertasForm f, BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) return pagina("alertas", model);
        try {
            configuracaoService.salvarAlertas(f);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return pagina("alertas", model);
        }
        return ok(ra, "alertas", "Configuração de alertas salva. Ela será aplicada na próxima verificação.");
    }

    // ---------------------------------------------------------------- canais de comunicação
    /** Recebe checkboxes "canal_D30=EMAIL", "canal_D7=WHATSAPP"... */
    @PostMapping("/canais")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public String canais(HttpServletRequest req, RedirectAttributes ra) {
        Map<String, Set<CanalEnvio>> mapa = new HashMap<>();
        req.getParameterMap().forEach((nome, valores) -> {
            if (!nome.startsWith("canal_")) return;
            Set<CanalEnvio> s = EnumSet.noneOf(CanalEnvio.class);
            for (String v : valores) {
                try { s.add(CanalEnvio.valueOf(v)); } catch (IllegalArgumentException ignorado) { /* valor inválido */ }
            }
            mapa.put(nome.substring(6), s);
        });
        regraCanalService.salvar(mapa);
        return ok(ra, "canais", "Canais por prazo de alerta salvos.");
    }

    @PostMapping("/canais/teste/{canal}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public String teste(@PathVariable CanalEnvio canal, RedirectAttributes ra) {
        ResultadoEnvio r = switch (canal) {
            case EMAIL -> canaisService.testarEmail();
            case WHATSAPP -> canaisService.testarWhatsapp();
            case SMS -> throw new NegocioException("O canal SMS ainda não possui provedor integrado.");
        };
        String nome = canal.getLabel();
        if (r.status() == StatusEnvio.ENVIADO) ra.addFlashAttribute("sucesso", nome + " de teste enviado. Verifique o recebimento.");
        else if (r.status() == StatusEnvio.MODO_DEV) ra.addFlashAttribute("info",
                "Modo desenvolvimento: a mensagem de teste por " + nome + " foi gerada em disco, mas NÃO foi enviada.");
        else if (r.status() == StatusEnvio.IGNORADO) ra.addFlashAttribute("info", r.erro());
        else ra.addFlashAttribute("erro", "Falha no teste de " + nome + ": " + r.erro());
        ra.addAttribute("aba", "canais");
        return "redirect:/app/configuracoes";
    }

    // ---------------------------------------------------------------- e-mail (personalização)
    @PostMapping("/email")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public String email(@Valid @ModelAttribute("emailForm") EmailConfigForm f, BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) return pagina("email", model);
        configuracaoService.salvarEmail(f);
        return ok(ra, "email", "Configuração de e-mail salva.");
    }

    // ---------------------------------------------------------------- sistema
    @PostMapping("/sistema")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public String sistema(@RequestParam int itensPorPagina, RedirectAttributes ra) {
        configuracaoService.salvarPreferenciasSistema(itensPorPagina);
        return ok(ra, "sistema", "Preferências do sistema salvas.");
    }

    // ---------------------------------------------------------------- categorias
    @PostMapping("/categorias")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public String categoria(@Valid @ModelAttribute("categoriaForm") CategoriaForm f, BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) return pagina("categorias", model);
        try {
            categoriaService.salvar(f);
        } catch (NegocioException e) {
            br.reject("negocio", e.getMessage());
            return pagina("categorias", model);
        }
        return ok(ra, "categorias", "Categoria salva.");
    }

    @PostMapping("/categorias/{id}/status")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public String categoriaStatus(@PathVariable Long id, RedirectAttributes ra) {
        categoriaService.alternarAtivo(id);
        return ok(ra, "categorias", "Situação da categoria atualizada.");
    }

    @PostMapping("/categorias/{id}/excluir")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public String categoriaExcluir(@PathVariable Long id, RedirectAttributes ra) {
        categoriaService.excluir(id);
        return ok(ra, "categorias", "Categoria excluída.");
    }

    // ---------------------------------------------------------------- permissões
    @PostMapping("/permissoes")
    @PreAuthorize("hasRole('ADMIN')")
    public String permissoes(@RequestParam PerfilCodigo perfil,
                             @RequestParam(name = "permissoes", required = false) List<PermissaoCodigo> permissoes,
                             RedirectAttributes ra) {
        Set<PermissaoCodigo> set = permissoes == null ? EnumSet.noneOf(PermissaoCodigo.class) : EnumSet.copyOf(permissoes);
        permissaoService.atualizar(perfil, set);
        return ok(ra, "permissoes", "Permissões do perfil " + perfil.getNome()
                + " atualizadas. Usuários desse perfil precisarão entrar novamente.");
    }

    // ---------------------------------------------------------------- LGPD
    @GetMapping("/meus-dados")
    public ResponseEntity<byte[]> meusDados() throws JsonProcessingException {
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(usuarioService.exportarMeusDados());
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("meus-dados-vigentra.json").build().toString())
                .body(json);
    }

    // ---------------------------------------------------------------- montagem da página
    private String pagina(String aba, Model model) {
        if (!ABAS.contains(aba)) aba = "perfil";
        Usuario u = usuarioService.atual();
        Configuracao c = configuracaoService.atual();
        model.addAttribute("aba", aba);
        model.addAttribute("usuario", u);
        model.addAttribute("config", c);
        if (!model.containsAttribute("empresaForm")) model.addAttribute("empresaForm", EmpresaForm.de(empresaService.atual()));
        if (!model.containsAttribute("perfilForm")) model.addAttribute("perfilForm", PerfilUsuarioForm.de(u));
        if (!model.containsAttribute("senhaForm")) model.addAttribute("senhaForm", new AlterarSenhaForm());
        if (!model.containsAttribute("alertasForm")) model.addAttribute("alertasForm", AlertasForm.de(c));
        if (!model.containsAttribute("emailForm")) model.addAttribute("emailForm", EmailConfigForm.de(c));
        if (!model.containsAttribute("categoriaForm")) model.addAttribute("categoriaForm", new CategoriaForm());
        if (!model.containsAttribute("prefForm")) {
            PreferenciasForm pf = PreferenciasForm.de(u.getPreferencias(), u.getCelular());
            pf.setNotificacoesApp(u.isNotificacoesApp());
            model.addAttribute("prefForm", pf);
        }
        model.addAttribute("categorias", categoriaService.todas());
        model.addAttribute("contagemCategorias", contagemCategorias());
        model.addAttribute("matriz", permissaoService.matriz());
        model.addAttribute("perfisCodigos", PerfilCodigo.values());
        model.addAttribute("permissoesTodas", PermissaoCodigo.values());
        model.addAttribute("integracoes", canaisService.status());
        model.addAttribute("statusEmail", canaisService.status(CanalEnvio.EMAIL));
        model.addAttribute("statusWhatsapp", canaisService.status(CanalEnvio.WHATSAPP));
        model.addAttribute("statusSms", canaisService.status(CanalEnvio.SMS));
        model.addAttribute("regras", regraCanalService.regras(u.getEmpresa().getId()));
        model.addAttribute("canais", CanalEnvio.values());
        model.addAttribute("diretorioDev", emailService.getDiretorioDev());
        model.addAttribute("cronAlertas", props.alerts().cron());
        model.addAttribute("baseUrl", props.baseUrl());
        return "app/configuracoes";
    }

    private Map<Long, Long> contagemCategorias() {
        Map<Long, Long> m = new LinkedHashMap<>();
        categoriaService.todas().forEach(c -> m.put(c.getId(), categoriaService.contarDocumentos(c.getId())));
        return m;
    }

    private String modoDev() {
        return emailService.isModoDesenvolvimento() ? " (Modo desenvolvimento: o e-mail foi gravado em disco, não enviado.)" : "";
    }

    private static String ok(RedirectAttributes ra, String aba, String msg) {
        ra.addFlashAttribute("sucesso", msg);
        ra.addAttribute("aba", aba);
        return "redirect:/app/configuracoes";
    }
}
