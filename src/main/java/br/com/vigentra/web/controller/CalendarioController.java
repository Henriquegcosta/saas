package br.com.vigentra.web.controller;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.StatusCalculator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Calendário interativo de vencimentos (a grade é montada em JavaScript a partir da API JSON). */
@Controller
public class CalendarioController {

    private final DocumentoService documentoService;

    public CalendarioController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping("/app/calendario")
    public String calendario(Model model) {
        model.addAttribute("hoje", documentoService.hoje());
        return "app/calendario";
    }

    public record EventoCalendario(Long id, String nome, String categoria, String cor, String data, String status,
                                   String statusLabel, String responsavel, String prazo) {}

    /** Documentos com vencimento no mês informado (sempre filtrados pela empresa do usuário). */
    @GetMapping("/app/api/calendario")
    @ResponseBody
    public Map<String, Object> eventos(@RequestParam int ano, @RequestParam int mes) {
        if (mes < 1 || mes > 12 || ano < 2000 || ano > 2100) throw new IllegalArgumentException("Mês inválido");
        YearMonth ym = YearMonth.of(ano, mes);
        LocalDate hoje = documentoService.hoje();
        int dias = documentoService.diasProximo();
        List<Documento> docs = documentoService.vencendoEntre(ym.atDay(1), ym.atEndOfMonth());
        List<EventoCalendario> eventos = docs.stream().map(d -> {
            StatusDocumento s = StatusCalculator.calcular(d, hoje, dias);
            return new EventoCalendario(d.getId(), d.getNome(), d.getCategoria().getNome(), d.getCategoria().getCor(),
                    d.getDataVencimento().toString(), s.name(), s.getLabel(),
                    d.getResponsavel() != null ? d.getResponsavel().getNome() : null, StatusCalculator.descreverPrazo(d, hoje));
        }).toList();
        return Map.of("ano", ano, "mes", mes, "hoje", hoje.toString(), "eventos", eventos);
    }
}
