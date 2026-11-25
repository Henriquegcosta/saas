package br.com.vigentra.web;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.ConfiguracaoService;
import br.com.vigentra.service.StatusCalculator;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Funções auxiliares para os templates Thymeleaf, acessíveis como ${@dg.metodo(...)}.
 * Com escopo de requisição: a configuração da empresa é lida uma única vez por página.
 */
@Component("dg")
@RequestScope
public class ViewHelper {

    private final ConfiguracaoService configuracaoService;
    private final Clock clock;
    private Integer diasProximo;

    public ViewHelper(ConfiguracaoService configuracaoService, Clock clock) {
        this.configuracaoService = configuracaoService;
        this.clock = clock;
    }

    private int diasProximo() {
        if (diasProximo == null) {
            diasProximo = SecurityUtils.usuarioOpcional().isPresent()
                    ? configuracaoService.atual().getDiasProximoVencimento() : 30;
        }
        return diasProximo;
    }

    public StatusDocumento status(Documento d) {
        return StatusCalculator.calcular(d, LocalDate.now(clock), diasProximo());
    }

    public String prazo(Documento d) {
        return StatusCalculator.descreverPrazo(d, LocalDate.now(clock));
    }

    public Long dias(Documento d) {
        return StatusCalculator.diasParaVencer(d, LocalDate.now(clock));
    }

    public String bytes(Long n) {
        if (n == null) return "";
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%.1f KB", n / 1024.0);
        return String.format("%.1f MB", n / (1024.0 * 1024));
    }

    public boolean imagem(Documento d) {
        return d.getArquivoTipo() != null && d.getArquivoTipo().startsWith("image/");
    }

    /** Destinatário formatado para exibição (telefones em formato legível). */
    public String contato(br.com.vigentra.domain.enums.CanalEnvio canal, String valor) {
        return br.com.vigentra.service.alerta.AlertaService.exibirContato(canal, valor);
    }

    /** Destinatário mascarado (painel da plataforma). */
    public String mascarar(String valor) {
        return br.com.vigentra.util.Mascaras.destinatario(valor);
    }

    public String telefone(String e164) {
        return br.com.vigentra.util.TelefoneUtils.formatar(e164);
    }

    /** Números de página exibidos na paginação (até 2 antes e 2 depois da atual). */
    public java.util.List<Integer> paginas(org.springframework.data.domain.Page<?> p) {
        int ini = Math.max(0, p.getNumber() - 2);
        int fim = Math.min(p.getTotalPages() - 1, p.getNumber() + 2);
        return java.util.stream.IntStream.rangeClosed(ini, fim).boxed().toList();
    }

    /** URL da página atual trocando apenas o parâmetro "pagina" (mantém filtros). */
    public String paginaUrl(int pagina) {
        var uri = ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("pagina", pagina).build();
        return uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
    }

    /** URL da página atual com um parâmetro alterado (null remove), voltando à 1ª página. */
    public String comParametro(String nome, Object valor) {
        var b = ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("pagina");
        if (valor == null) b.replaceQueryParam(nome); else b.replaceQueryParam(nome, valor);
        var uri = b.build();
        return uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
    }

    /** Query string atual (para repassar os filtros aos links de exportação). */
    public String queryAtual() {
        var q = ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("pagina").build().getQuery();
        return q == null ? "" : "?" + q;
    }
}
