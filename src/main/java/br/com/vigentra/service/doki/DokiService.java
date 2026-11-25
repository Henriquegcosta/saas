package br.com.vigentra.service.doki;

import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.doki.DokiConsultaService.Lista;
import br.com.vigentra.service.doki.RespostaDoki.Link;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Doki - assistente virtual do VIGENTRA. "O Doki está de olho. Seus documentos, sempre protegidos."
 *
 * Camada de REGRAS: recebe a pergunta, pede a interpretação ao {@link InterpretadorPerguntas},
 * busca dados reais no {@link DokiConsultaService} e monta a resposta.
 * - Nunca inventa dados: quantidades e documentos vêm sempre do banco.
 * - Somente leitura: o Doki não cadastra, altera nem exclui nada.
 * - Respeita as permissões do perfil: só sugere telas que o usuário pode acessar.
 */
@Service
public class DokiService {

    public static final String NOME = "Doki";
    public static final String SLOGAN = "O Doki está de olho. Seus documentos, sempre protegidos.";

    /** Quantos documentos aparecem na conversa (o restante é acessado pelo link "Ver todos"). */
    static final int LIMITE_LISTA = 5;

    static final List<String> SUGESTOES = List.of(
            "Quais documentos estão próximos do vencimento?",
            "Quais documentos já venceram?",
            "Quantos documentos estão cadastrados?",
            "Quais certificados precisam de atenção?",
            "Como cadastro um novo documento?");

    private final InterpretadorPerguntas interpretador;
    private final DokiConsultaService consultas;

    public DokiService(InterpretadorPerguntas interpretador, DokiConsultaService consultas) {
        this.interpretador = interpretador;
        this.consultas = consultas;
    }

    public RespostaDoki responder(String pergunta) {
        Interpretacao i = interpretador.interpretar(pergunta);
        UsuarioPrincipal u = SecurityUtils.usuario();
        return switch (i.intencao()) {
            case DOCUMENTOS_PROXIMOS -> proximos(u, i.dias());
            case DOCUMENTOS_VENCIDOS -> vencidos(u);
            case TOTAL_DOCUMENTOS -> total(u);
            case CERTIFICADOS_ATENCAO -> certificados(u);
            case COMO_CADASTRAR_DOCUMENTO -> comoCadastrar(u, false);
            case COMO_CADASTRAR_CERTIFICADO -> comoCadastrar(u, true);
            case COMO_ATUALIZAR_DOCUMENTO -> comoAtualizar(u);
            case COMO_USAR_CALENDARIO -> calendario();
            case ONDE_RELATORIOS -> relatorios(u);
            case COMO_CONVIDAR_USUARIO -> convidar(u);
            case SAUDACAO -> conversa(Intencao.SAUDACAO, "Olá, " + u.getPrimeiroNome() + "! Eu sou o Doki! 🤖 Estou de olho nos seus "
                    + "documentos para ajudar você a manter tudo organizado e protegido. Como posso ajudar?");
            case AGRADECIMENTO -> conversa(Intencao.AGRADECIMENTO, "Por nada! 😊 Sempre que precisar, é só chamar. O Doki está de olho!");
            case AJUDA -> ajuda();
            case DESCONHECIDA -> naoEntendi();
        };
    }

    // =================================================================== consultas (dados reais)

    private RespostaDoki proximos(UsuarioPrincipal u, Integer diasPedidos) {
        int dias = diasPedidos != null ? diasPedidos : consultas.diasProximoPadrao();
        Lista l = consultas.proximos(dias, LIMITE_LISTA);
        String periodo = dias == 0 ? "hoje" : dias == 1 ? "até amanhã" : "nos próximos " + dias + " dias";
        LocalDate hoje = consultas.hoje();
        String texto;
        if (l.total() == 0) {
            texto = "Boa notícia! 🎉 Nenhum documento vence " + periodo + escopo(u) + ".";
        } else {
            texto = (l.total() == 1 ? "Encontrei 1 documento que vence " : "Encontrei " + l.total() + " documentos que vencem ")
                    + periodo + escopo(u) + maisNaLista(l) + ":";
        }
        List<Link> links = new ArrayList<>();
        if (l.total() > 0) links.add(new Link("Ver na lista de documentos", "/app/documentos?vencimentoDe=" + hoje
                + "&vencimentoAte=" + hoje.plusDays(dias)));
        links.add(new Link("Abrir o calendário", "/app/calendario"));
        // complemento com dado real: quem pergunta o que precisa renovar também precisa saber dos já vencidos
        long jaVencidos = consultas.contagemPorStatus().getOrDefault(StatusDocumento.VENCIDO, 0L);
        List<String> passos = jaVencidos == 0 ? List.of() : List.of("Além disso, " + (jaVencidos == 1
                ? "1 documento já venceu" : jaVencidos + " documentos já venceram") + ". Pergunte \"Quais documentos já venceram?\" para ver.");
        return new RespostaDoki(Intencao.DOCUMENTOS_PROXIMOS.name(), texto, passos, l.documentos(), l.total(), links,
                List.of("Quais documentos já venceram?", "Existem documentos vencendo nos próximos 7 dias?"));
    }

    private RespostaDoki vencidos(UsuarioPrincipal u) {
        Lista l = consultas.vencidos(LIMITE_LISTA);
        if (l.total() == 0) {
            return new RespostaDoki(Intencao.DOCUMENTOS_VENCIDOS.name(), "Tudo em dia! ✅ Nenhum documento está vencido" + escopo(u) + ".",
                    List.of(), List.of(), 0L, List.of(), List.of("Quais documentos estão próximos do vencimento?"));
        }
        String texto = "Atenção: " + (l.total() == 1 ? "1 documento já venceu" : l.total() + " documentos já venceram")
                + escopo(u) + maisNaLista(l) + ":";
        List<String> passos = new ArrayList<>(List.of(u.tem(PermissaoCodigo.DOC_EDIT)
                ? "Para regularizar, abra o documento, clique em \"Editar\", informe a nova data de vencimento e anexe a versão renovada."
                : "Para regularizar, fale com o responsável pelo documento ou com um administrador da sua empresa."));
        long proximos = consultas.contagemPorStatus().getOrDefault(StatusDocumento.PROXIMO_VENCIMENTO, 0L);
        if (proximos > 0) {
            passos.add("Além disso, " + (proximos == 1 ? "1 documento vence" : proximos + " documentos vencem") + " nos próximos "
                    + consultas.diasProximoPadrao() + " dias.");
        }
        return new RespostaDoki(Intencao.DOCUMENTOS_VENCIDOS.name(), texto, passos, l.documentos(), l.total(),
                List.of(new Link("Ver todos os vencidos", "/app/documentos?status=VENCIDO")),
                List.of("Quais documentos estão próximos do vencimento?", "Como atualizo as informações de um documento?"));
    }

    private RespostaDoki total(UsuarioPrincipal u) {
        Map<StatusDocumento, Long> c = consultas.contagemPorStatus();
        long total = c.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            List<Link> links = u.tem(PermissaoCodigo.DOC_CREATE) ? List.of(new Link("Cadastrar documento", "/app/documentos/novo")) : List.of();
            return new RespostaDoki(Intencao.TOTAL_DOCUMENTOS.name(), "Ainda não há documentos cadastrados" + escopo(u) + ".",
                    List.of(), List.of(), 0L, links, List.of("Como cadastro um novo documento?"));
        }
        String texto = (total == 1 ? "Há 1 documento cadastrado" : "Há " + total + " documentos cadastrados") + escopo(u) + ":";
        List<String> detalhes = List.of(
                plural(c.get(StatusDocumento.REGULAR), "regular", "regulares"),
                plural(c.get(StatusDocumento.PROXIMO_VENCIMENTO), "próximo do vencimento", "próximos do vencimento"),
                plural(c.get(StatusDocumento.VENCIDO), "vencido", "vencidos"),
                plural(c.get(StatusDocumento.SEM_VENCIMENTO), "sem vencimento", "sem vencimento"));
        return new RespostaDoki(Intencao.TOTAL_DOCUMENTOS.name(), texto, detalhes, List.of(), total,
                List.of(new Link("Abrir documentos", "/app/documentos"), new Link("Ver dashboard", "/app/dashboard")),
                List.of("Quais documentos já venceram?", "Quais documentos estão próximos do vencimento?"));
    }

    private RespostaDoki certificados(UsuarioPrincipal u) {
        Lista l = consultas.certificadosAtencao(LIMITE_LISTA);
        String texto = l.total() == 0
                ? "Nenhum certificado vencido ou próximo do vencimento" + escopo(u) + ". 🛡️"
                : (l.total() == 1 ? "1 certificado precisa de atenção" : l.total() + " certificados precisam de atenção")
                  + escopo(u) + " (vencidos ou vencendo nos próximos " + consultas.diasProximoPadrao() + " dias)" + maisNaLista(l) + ":";
        return new RespostaDoki(Intencao.CERTIFICADOS_ATENCAO.name(), texto,
                List.of("Considero certificado todo documento cujo nome ou categoria contenha a palavra \"certificado\"."),
                l.documentos(), l.total(), List.of(new Link("Buscar certificados", "/app/documentos?q=certificado")),
                List.of("Como cadastro um certificado?", "Quais documentos já venceram?"));
    }

    // =================================================================== orientações de uso

    private RespostaDoki comoCadastrar(UsuarioPrincipal u, boolean certificado) {
        Intencao intencao = certificado ? Intencao.COMO_CADASTRAR_CERTIFICADO : Intencao.COMO_CADASTRAR_DOCUMENTO;
        if (!u.tem(PermissaoCodigo.DOC_CREATE)) return semPermissao(intencao, "cadastrar documentos");
        List<String> passos = new ArrayList<>();
        passos.add("Clique em \"Novo documento\", no topo da tela (ou em Documentos → Novo documento).");
        if (certificado) {
            passos.add("Em \"Nome do documento\", descreva o certificado (ex.: Certificado Digital e-CNPJ A1).");
            passos.add("Em \"Categoria\", escolha uma categoria de certificados (ex.: Certificados Digitais).");
            passos.add("Em \"Número ou identificação\", informe o número de série ou o registro do certificado.");
        } else {
            passos.add("Preencha o \"Nome do documento\" e escolha a \"Categoria\" (campos obrigatórios).");
        }
        passos.add("Informe a \"Data de vencimento\" - ou marque \"Documento sem vencimento\".");
        passos.add("Se quiser, preencha emissão, responsável (nome e e-mail) e anexe o arquivo (PDF ou imagem).");
        passos.add("Clique em \"Salvar\". A situação é calculada automaticamente e eu passo a ficar de olho no vencimento! 👀");
        if (certificado) passos.add("Por segurança, anexe apenas o comprovante (PDF ou imagem) - nunca o arquivo do certificado (.pfx) nem a senha.");
        List<Link> links = new ArrayList<>(List.of(new Link(certificado ? "Cadastrar certificado" : "Cadastrar documento", "/app/documentos/novo")));
        if (certificado && u.tem(PermissaoCodigo.CATEGORY_MANAGE)) {
            links.add(new Link("Gerenciar categorias", "/app/configuracoes?aba=categorias"));
        }
        return orientacao(intencao, certificado ? "Para cadastrar um certificado, siga estes passos:" : "Para cadastrar um novo documento, siga estes passos:",
                passos, links);
    }

    private RespostaDoki comoAtualizar(UsuarioPrincipal u) {
        if (!u.tem(PermissaoCodigo.DOC_EDIT)) return semPermissao(Intencao.COMO_ATUALIZAR_DOCUMENTO, "editar documentos");
        return orientacao(Intencao.COMO_ATUALIZAR_DOCUMENTO, "Para atualizar as informações de um documento:", List.of(
                "Acesse \"Documentos\" e localize o documento (a busca no topo ajuda).",
                "Abra o documento e clique em \"Editar\".",
                "Altere o que for necessário - em uma renovação, informe a nova data de vencimento e anexe a nova versão.",
                "Clique em \"Salvar\". As alterações ficam registradas no histórico do documento."),
                List.of(new Link("Abrir documentos", "/app/documentos")));
    }

    private RespostaDoki calendario() {
        return orientacao(Intencao.COMO_USAR_CALENDARIO, "O calendário mostra, mês a mês, os documentos de acordo com a data de vencimento:", List.of(
                "Acesse \"Calendário\" no menu lateral.",
                "Use as setas para mudar de mês ou \"Hoje\" para voltar ao mês atual.",
                "As cores indicam a situação: vermelho = vencido, amarelo = próximo do vencimento, verde = regular.",
                "Clique em uma data para ver os documentos que vencem nela e abrir os detalhes."),
                List.of(new Link("Abrir o calendário", "/app/calendario")));
    }

    private RespostaDoki relatorios(UsuarioPrincipal u) {
        if (!u.tem(PermissaoCodigo.REPORT_VIEW)) return semPermissao(Intencao.ONDE_RELATORIOS, "consultar relatórios");
        List<String> passos = new ArrayList<>(List.of(
                "Acesse \"Relatórios\" no menu lateral.",
                "Use os filtros (situação, categoria, responsável, período) para montar a visão desejada.",
                "Confira os gráficos de vencimentos por mês e por categoria e o relatório por responsável."));
        passos.add(u.tem(PermissaoCodigo.REPORT_EXPORT)
                ? "Para exportar, use o botão de exportação: PDF (para impressão), Excel (.xlsx) ou CSV."
                : "A exportação (PDF, Excel, CSV) depende de permissão: peça a um administrador, se precisar.");
        return orientacao(Intencao.ONDE_RELATORIOS, "Os relatórios ficam no menu \"Relatórios\":", passos,
                List.of(new Link("Abrir relatórios", "/app/relatorios")));
    }

    private RespostaDoki convidar(UsuarioPrincipal u) {
        if (!u.tem(PermissaoCodigo.USER_MANAGE)) return semPermissao(Intencao.COMO_CONVIDAR_USUARIO, "convidar usuários");
        return orientacao(Intencao.COMO_CONVIDAR_USUARIO, "Para convidar alguém para a sua empresa no VIGENTRA:", List.of(
                "Acesse Administração → \"Usuários\" e clique em \"Convidar usuário\".",
                "Informe o e-mail da pessoa e escolha o perfil de acesso.",
                "Se ela também for cuidar de documentos, marque \"Cadastrar também como responsável por documentos\".",
                "Envie o convite: a pessoa recebe um e-mail com o link para criar a senha e entrar."),
                List.of(new Link("Convidar usuário", "/app/usuarios/convidar")));
    }

    // =================================================================== conversa

    private RespostaDoki ajuda() {
        return new RespostaDoki(Intencao.AJUDA.name(), "Eu sou o Doki, o assistente do VIGENTRA! Posso ajudar com:", List.of(
                "Documentos vencidos ou próximos do vencimento (em 7, 15, 30 dias...).",
                "Quantidade de documentos cadastrados e certificados que precisam de atenção.",
                "Como cadastrar e atualizar documentos e certificados.",
                "Como usar o calendário, encontrar os relatórios e convidar usuários."),
                List.of(), null, List.of(), SUGESTOES);
    }

    private RespostaDoki naoEntendi() {
        return new RespostaDoki(Intencao.DESCONHECIDA.name(), "Hmm, não entendi essa pergunta. 🤔 Ainda estou aprendendo! "
                + "Tente perguntar de outro jeito ou escolha um dos assuntos abaixo:", List.of(), List.of(), null, List.of(), SUGESTOES);
    }

    private RespostaDoki conversa(Intencao i, String texto) {
        return new RespostaDoki(i.name(), texto, List.of(), List.of(), null, List.of(), SUGESTOES);
    }

    private RespostaDoki orientacao(Intencao i, String texto, List<String> passos, List<Link> links) {
        return new RespostaDoki(i.name(), texto, passos, List.of(), null, links, List.of());
    }

    private RespostaDoki semPermissao(Intencao i, String acao) {
        return new RespostaDoki(i.name(), "Seu perfil de acesso não tem permissão para " + acao + ". "
                + "Fale com um administrador da sua empresa: ele pode realizar a ação ou ajustar suas permissões.",
                List.of(), List.of(), null, List.of(), List.of());
    }

    // =================================================================== utilitários

    /** Usuários sem DOC_VIEW_ALL só enxergam os próprios documentos: a resposta deixa isso claro. */
    private static String escopo(UsuarioPrincipal u) {
        return u.tem(PermissaoCodigo.DOC_VIEW_ALL) ? "" : " entre os documentos que você pode visualizar";
    }

    private static String maisNaLista(Lista l) {
        return l.total() > l.documentos().size() ? " (mostrando os " + l.documentos().size() + " primeiros)" : "";
    }

    private static String plural(Long n, String singular, String plural) {
        long v = n == null ? 0 : n;
        return v + " " + (v == 1 ? singular : plural);
    }
}
