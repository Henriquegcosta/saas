package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.NotificacaoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard: indicadores, gráficos, próximos vencimentos e atividades recentes. */
@Controller
public class DashboardController {

    private final DocumentoService documentoService;
    private final NotificacaoService notificacaoService;
    private final ObjectMapper mapper;

    public DashboardController(DocumentoService documentoService, NotificacaoService notificacaoService,
                               ObjectMapper mapper) {
        this.documentoService = documentoService;
        this.notificacaoService = notificacaoService;
        this.mapper = mapper;
    }

    @GetMapping("/app/dashboard")
    public String dashboard(Model model) throws JsonProcessingException {
        Map<StatusDocumento, Long> totais = documentoService.contagemPorStatus();
        long total = totais.values().stream().mapToLong(Long::longValue).sum();
        model.addAttribute("total", total);
        model.addAttribute("qRegular", totais.get(StatusDocumento.REGULAR));
        model.addAttribute("qProximo", totais.get(StatusDocumento.PROXIMO_VENCIMENTO));
        model.addAttribute("qVencido", totais.get(StatusDocumento.VENCIDO));
        model.addAttribute("qSem", totais.get(StatusDocumento.SEM_VENCIMENTO));
        model.addAttribute("hoje", documentoService.hoje());
        model.addAttribute("diasProximo", documentoService.diasProximo());
        model.addAttribute("proximos", documentoService.proximosVencimentos(8, 90));
        model.addAttribute("vencidos", documentoService.vencidos(5));
        model.addAttribute("ultimos", documentoService.ultimosCadastrados(5));
        model.addAttribute("historico", SecurityUtils.tem(PermissaoCodigo.NOTIF_MANAGE) || SecurityUtils.tem(PermissaoCodigo.DOC_VIEW_ALL)
                ? notificacaoService.historicoRecente(6) : List.of());

        // gráfico de situação (rosca)
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("labels", List.of("Regulares", "Próximos do vencimento", "Vencidos", "Sem vencimento"));
        status.put("valores", List.of(totais.get(StatusDocumento.REGULAR), totais.get(StatusDocumento.PROXIMO_VENCIMENTO),
                totais.get(StatusDocumento.VENCIDO), totais.get(StatusDocumento.SEM_VENCIMENTO)));
        model.addAttribute("graficoStatus", mapper.writeValueAsString(status));

        // gráfico de vencimentos por mês (2 meses atrás + 9 à frente)
        YearMonth inicio = YearMonth.from(documentoService.hoje()).minusMonths(2);
        YearMonth fim = inicio.plusMonths(11);
        List<Documento> docs = documentoService.vencendoEntre(inicio.atDay(1), fim.atEndOfMonth());
        List<String> rotulos = new ArrayList<>();
        List<Long> vencidos = new ArrayList<>();
        List<Long> aVencer = new ArrayList<>();
        Locale pt = Locale.forLanguageTag("pt-BR");
        for (YearMonth m = inicio; !m.isAfter(fim); m = m.plusMonths(1)) {
            YearMonth mes = m;
            rotulos.add(m.getMonth().getDisplayName(TextStyle.SHORT, pt).replace(".", "") + "/" + String.valueOf(m.getYear()).substring(2));
            vencidos.add(docs.stream().filter(d -> YearMonth.from(d.getDataVencimento()).equals(mes)
                    && d.getDataVencimento().isBefore(documentoService.hoje())).count());
            aVencer.add(docs.stream().filter(d -> YearMonth.from(d.getDataVencimento()).equals(mes)
                    && !d.getDataVencimento().isBefore(documentoService.hoje())).count());
        }
        model.addAttribute("graficoMeses", mapper.writeValueAsString(Map.of("labels", rotulos, "vencidos", vencidos, "aVencer", aVencer)));
        return "app/dashboard";
    }
}
