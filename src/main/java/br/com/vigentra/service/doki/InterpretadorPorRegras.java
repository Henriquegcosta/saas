package br.com.vigentra.service.doki;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpretação por REGRAS (sem IA e sem custo), baseada em CONCEITOS - não em frases exatas.
 *
 * Etapas:
 * 1. Normalização: minúsculas, sem acentos e sem pontuação ("Próximos" -> "proximos").
 * 2. Expressões equivalentes: frases de várias palavras viram uma palavra-chave
 *    ("fora da validade" -> "vencido", "vai vencer" -> "vencer", "bom dia" -> "oi").
 * 3. Cada palavra é ligada a um {@link Conceito}: por palavra exata, por radical
 *    ("renov" cobre renovar, renovados, renovação...) ou, se não for conhecida,
 *    pela palavra mais parecida do vocabulário (tolera erros de digitação: "documetos", "vensidos").
 * 4. Decisão: combinações de conceitos definem a intenção. Pedidos de orientação ("como...")
 *    são avaliados antes das consultas, mas perguntas de consulta ("quais...", "quantos...")
 *    nunca viram orientação por engano.
 * 5. Extração do prazo citado ("7 dias", "semana", "quinzena", "mês", "hoje", "amanhã").
 */
@Component
public class InterpretadorPorRegras implements InterpretadorPerguntas {

    enum Conceito {
        DOCUMENTO, CERTIFICADO, VENCIDO, PROXIMO, RENOVAR, ATENCAO, QUANTIDADE, SITUACAO, LISTAR, COMO,
        CADASTRAR, ATUALIZAR, CONVIDAR, USUARIO, CALENDARIO, RELATORIO, AJUDA, AGRADECIMENTO, SAUDACAO
    }

    /** Maior prazo aceito em uma consulta (evita consultas desproporcionais). */
    static final int DIAS_MAXIMO = 365;

    // ------------------------------------------------------------------ 2. expressões equivalentes (ordem importa)
    private static final Map<Pattern, String> EXPRESSOES = new LinkedHashMap<>();
    static {
        expr("o que (voce )?(sabe|faz|pode|consegue)( fazer)?|quem e voce|para que (voce )?serve|como voce funciona"
                + "|como (voce )?pode (me )?ajudar|em que (voce )?pode (me )?ajudar", "ajuda");
        expr("bom dia|boa tarde|boa noite|tudo bem|tudo bom|e ai", "oi");
        expr("fora (da|de) validade|fora do prazo|validade (vencida|expirada|esgotada|venceu)|perdeu a validade"
                + "|perderam a validade|passou do prazo|passaram do prazo|ja passou|nao (esta|estao) (mais )?valid[oa]s?", "vencido");
        expr("(vai|vao|ira|irao|esta para|estao para|para|a|perto de|proximos? de|prestes a|quase) (vencer|expirar)", "vencer");
        expr("dar acesso|liberar acesso|dar permissao|novo usuario|nova pessoa|novo membro|novo colaborador", "convidar usuario");
        expr("novo certificado", "cadastrar certificado");
        expr("nov[oa] (documento|certidao|licenca|alvara|contrato)", "cadastrar documento");
        expr("tem como|ha como|e possivel|da para|da pra|o que (eu )?faco para|o que fazer para", "como");
        expr("visao geral|status geral|como (esta|estao) (os )?(meus |nossos )?documentos", "situacao");
        expr("e cnpj|e cpf", "certificado");
    }

    private static void expr(String regex, String chave) {
        EXPRESSOES.put(Pattern.compile("\\b(" + regex + ")\\b"), chave);
    }

    // ------------------------------------------------------------------ 3. vocabulário: palavras exatas e radicais
    private static final Map<String, Conceito> PALAVRAS = new HashMap<>();
    /** Radicais testados na ordem (mais específicos primeiro). */
    private static final List<Map.Entry<String, Conceito>> RADICAIS = new ArrayList<>();
    /** Palavras completas usadas na correção de erros de digitação. */
    private static final Map<String, Conceito> DICIONARIO = new HashMap<>();

    static {
        palavras(Conceito.DOCUMENTO, "doc", "docs", "certidao", "certidoes", "licenca", "licencas", "alvara", "alvaras",
                "contrato", "contratos", "papeis");
        radicais(Conceito.DOCUMENTO, "document");
        palavras(Conceito.CERTIFICADO, "cert", "certs", "ecnpj", "ecpf");
        radicais(Conceito.CERTIFICADO, "certific");

        palavras(Conceito.VENCIDO, "venceu", "venceram", "expirou", "expiraram", "caducou", "caducaram");
        radicais(Conceito.VENCIDO, "vencid", "expirad", "atrasad", "atraso", "caducad", "invalid");
        palavras(Conceito.PROXIMO, "vence", "vencem", "vencer", "vencera", "vencerao", "venceria", "expira", "expiram",
                "expirar", "perto", "prazo", "prazos", "validade", "validades");
        radicais(Conceito.PROXIMO, "venciment", "vencend", "expirand", "proxim", "prestes");
        radicais(Conceito.RENOVAR, "renov");
        radicais(Conceito.ATENCAO, "atenc", "irregular", "pendent", "pendenc", "urgent", "critic", "problem", "risco",
                "regulariz", "cuidad");

        palavras(Conceito.QUANTIDADE, "total", "totais", "numero", "contagem");
        radicais(Conceito.QUANTIDADE, "quant");
        palavras(Conceito.SITUACAO, "status", "geral", "panorama");
        radicais(Conceito.SITUACAO, "situac", "resum");
        palavras(Conceito.LISTAR, "quais", "qual", "ver", "veja", "vejo", "tem", "temos", "tenho", "ha", "existe", "existem",
                "algum", "alguma", "alguns", "algo", "informa", "informe", "diga", "diz", "fala", "fale", "traga", "traz");
        radicais(Conceito.LISTAR, "list", "mostr", "exib", "visualiz", "consult", "verific");
        palavras(Conceito.COMO, "como", "onde", "posso", "consigo", "devo", "faco", "procedimento");
        radicais(Conceito.COMO, "ensin", "explic", "tutorial", "orient", "passo");

        palavras(Conceito.CADASTRAR, "criar", "crio", "cria", "crie", "subir", "subo", "enviar", "envio", "upload");
        radicais(Conceito.CADASTRAR, "cadastr", "adicion", "inclu", "registr", "insir", "inser", "anex", "lanc");
        palavras(Conceito.ATUALIZAR, "mudar", "mudo", "muda", "trocar", "troco", "troca");
        radicais(Conceito.ATUALIZAR, "atualiz", "edit", "alter", "modific", "corrig", "substitu");
        radicais(Conceito.CONVIDAR, "convid", "convite");
        palavras(Conceito.USUARIO, "pessoa", "pessoas", "equipe");
        radicais(Conceito.USUARIO, "usuari", "colega", "membro", "funcionari", "colaborador");
        radicais(Conceito.CALENDARIO, "calendari", "agenda", "cronogram");
        palavras(Conceito.RELATORIO, "excel", "csv", "pdf", "xlsx");
        radicais(Conceito.RELATORIO, "relatori", "export", "planilh", "grafic", "imprim", "impress", "indicador");

        palavras(Conceito.AJUDA, "help", "socorro", "comandos", "opcoes", "menu", "duvida", "duvidas", "funcionalidades");
        radicais(Conceito.AJUDA, "ajud");
        palavras(Conceito.AGRADECIMENTO, "valeu", "vlw", "thanks", "obg", "grato", "grata");
        radicais(Conceito.AGRADECIMENTO, "obrigad", "agradec", "brigad");
        palavras(Conceito.SAUDACAO, "oi", "oie", "oii", "oiii", "ola", "opa", "eai", "hey", "hello", "salve", "alo", "doki");

        // palavras completas comuns, para corrigir erros de digitação dos radicais
        for (String p : List.of("documento", "documentos", "certificado", "certificados", "vencido", "vencidos", "vencida",
                "vencidas", "vencimento", "vencimentos", "vencendo", "expirado", "expirados", "atrasado", "atrasados",
                "renovar", "renovados", "renovacao", "proximo", "proximos", "quantos", "quantas", "quantidade", "cadastrar",
                "cadastro", "cadastrados", "atualizar", "editar", "alterar", "convidar", "convite", "usuario", "usuarios",
                "calendario", "relatorio", "relatorios", "exportar", "planilha", "graficos", "pendente", "pendentes",
                "irregular", "irregulares", "situacao", "resumo", "atencao", "obrigado", "obrigada", "ajuda", "mostrar",
                "listar", "adicionar", "registrar", "incluir")) {
            Conceito c = porRadical(p);
            if (c != null) DICIONARIO.put(p, c);
        }
        DICIONARIO.putAll(PALAVRAS);
    }

    private static void palavras(Conceito c, String... ps) {
        for (String p : ps) PALAVRAS.put(p, c);
    }

    private static void radicais(Conceito c, String... rs) {
        for (String r : rs) RADICAIS.add(Map.entry(r, c));
    }

    // ------------------------------------------------------------------ 5. prazo
    private static final Pattern DIAS = Pattern.compile("\\b(\\d{1,3})\\s*dias?\\b");
    private static final Pattern SEMANA = Pattern.compile("\\bsemana\\b");
    private static final Pattern QUINZENA = Pattern.compile("\\bquinzena\\b");
    private static final Pattern MES = Pattern.compile("\\bmes\\b");
    private static final Pattern HOJE = Pattern.compile("\\bhoje\\b");
    private static final Pattern AMANHA = Pattern.compile("\\bamanha\\b");

    @Override
    public Interpretacao interpretar(String pergunta) {
        String t = normalizar(pergunta);
        if (t.isEmpty()) return Interpretacao.de(Intencao.DESCONHECIDA);
        String comExpressoes = t;
        for (Map.Entry<Pattern, String> e : EXPRESSOES.entrySet()) {
            comExpressoes = e.getKey().matcher(comExpressoes).replaceAll(e.getValue());
        }
        Set<Conceito> c = conceitos(comExpressoes);
        Intencao intencao = decidir(c);
        return new Interpretacao(intencao, intencao == Intencao.DOCUMENTOS_PROXIMOS ? prazo(t) : null);
    }

    /** 4. Combinação de conceitos -> intenção. */
    static Intencao decidir(Set<Conceito> c) {
        boolean consulta = c.contains(Conceito.LISTAR) || c.contains(Conceito.QUANTIDADE) || c.contains(Conceito.SITUACAO);
        boolean pedidoDeOrientacao = c.contains(Conceito.COMO) || !consulta;

        // orientações de uso
        if (c.contains(Conceito.CONVIDAR) || (c.contains(Conceito.USUARIO) && c.contains(Conceito.CADASTRAR) && pedidoDeOrientacao)) {
            return Intencao.COMO_CONVIDAR_USUARIO;
        }
        if (c.contains(Conceito.CALENDARIO)) return Intencao.COMO_USAR_CALENDARIO;
        if (c.contains(Conceito.RELATORIO)) return Intencao.ONDE_RELATORIOS;
        if (c.contains(Conceito.CADASTRAR) && pedidoDeOrientacao) {
            return c.contains(Conceito.CERTIFICADO) ? Intencao.COMO_CADASTRAR_CERTIFICADO : Intencao.COMO_CADASTRAR_DOCUMENTO;
        }
        if ((c.contains(Conceito.ATUALIZAR) && pedidoDeOrientacao) || (c.contains(Conceito.RENOVAR) && c.contains(Conceito.COMO))) {
            return Intencao.COMO_ATUALIZAR_DOCUMENTO;
        }

        // consultas a dados reais
        if (c.contains(Conceito.CERTIFICADO)) return Intencao.CERTIFICADOS_ATENCAO;
        if (c.contains(Conceito.VENCIDO) || c.contains(Conceito.ATENCAO)) return Intencao.DOCUMENTOS_VENCIDOS;
        if (c.contains(Conceito.PROXIMO) || c.contains(Conceito.RENOVAR)) return Intencao.DOCUMENTOS_PROXIMOS;
        if (c.contains(Conceito.QUANTIDADE) || c.contains(Conceito.SITUACAO)
                || (c.contains(Conceito.DOCUMENTO) && c.contains(Conceito.LISTAR))) {
            return Intencao.TOTAL_DOCUMENTOS;
        }

        // conversa
        if (c.contains(Conceito.AJUDA) || c.contains(Conceito.COMO)) return Intencao.AJUDA;
        if (c.contains(Conceito.AGRADECIMENTO)) return Intencao.AGRADECIMENTO;
        if (c.contains(Conceito.SAUDACAO)) return Intencao.SAUDACAO;
        if (c.contains(Conceito.DOCUMENTO)) return Intencao.TOTAL_DOCUMENTOS;
        return Intencao.DESCONHECIDA;
    }

    static Set<Conceito> conceitos(String texto) {
        Set<Conceito> c = EnumSet.noneOf(Conceito.class);
        for (String palavra : texto.split(" ")) {
            Conceito k = conceito(palavra);
            if (k != null) c.add(k);
        }
        return c;
    }

    /** Palavra exata -> radical -> palavra mais parecida (erro de digitação). */
    static Conceito conceito(String palavra) {
        if (palavra.isEmpty()) return null;
        Conceito c = PALAVRAS.get(palavra);
        if (c == null) c = porRadical(palavra);
        if (c == null) c = porSemelhanca(palavra);
        return c;
    }

    private static Conceito porRadical(String palavra) {
        for (Map.Entry<String, Conceito> r : RADICAIS) {
            if (palavra.startsWith(r.getKey())) return r.getValue();
        }
        return null;
    }

    /**
     * Correção de erros de digitação: procura a palavra do vocabulário mais parecida
     * (distância de edição com transposição). Só vale para palavras com 4+ letras, com a mesma
     * letra inicial, até 1 erro (até 7 letras) ou 2 erros (8+ letras). Empate entre conceitos = ignora.
     */
    private static Conceito porSemelhanca(String palavra) {
        if (palavra.length() < 4) return null;
        int limite = palavra.length() >= 8 ? 2 : 1;
        int melhor = Integer.MAX_VALUE;
        Conceito escolhido = null;
        boolean empate = false;
        for (Map.Entry<String, Conceito> e : DICIONARIO.entrySet()) {
            String w = e.getKey();
            if (w.length() < 4 || w.charAt(0) != palavra.charAt(0) || Math.abs(w.length() - palavra.length()) > limite) continue;
            int d = distancia(palavra, w, limite);
            if (d > limite) continue;
            if (d < melhor) {
                melhor = d;
                escolhido = e.getValue();
                empate = false;
            } else if (d == melhor && e.getValue() != escolhido) {
                empate = true;
            }
        }
        return empate ? null : escolhido;
    }

    /** Distância de Damerau-Levenshtein (versão "optimal string alignment"), com corte em "limite". */
    static int distancia(String a, String b, int limite) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int menorDaLinha = Integer.MAX_VALUE;
            for (int j = 1; j <= b.length(); j++) {
                int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + custo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
                menorDaLinha = Math.min(menorDaLinha, d[i][j]);
            }
            if (menorDaLinha > limite) return limite + 1;
        }
        return d[a.length()][b.length()];
    }

    /** Minúsculas, sem acentos e sem pontuação, com espaços simples. */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Integer prazo(String t) {
        Matcher m = DIAS.matcher(t);
        if (m.find()) return Math.min(Math.max(Integer.parseInt(m.group(1)), 0), DIAS_MAXIMO);
        if (QUINZENA.matcher(t).find()) return 15;
        if (SEMANA.matcher(t).find()) return 7;
        if (MES.matcher(t).find()) return 30;
        if (AMANHA.matcher(t).find()) return 1;
        if (HOJE.matcher(t).find()) return 0;
        return null;
    }
}
