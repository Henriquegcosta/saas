package br.com.vigentra.web.controller;

import br.com.vigentra.security.LimitadorTaxa;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.service.doki.DokiService;
import br.com.vigentra.service.doki.RespostaDoki;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * API do assistente Doki (área logada, JSON).
 *
 * Segurança:
 * - /app/** exige autenticação (SecurityConfig) e e-mail confirmado (EmailVerificadoFilter);
 * - POST protegido por CSRF (token lido das meta tags do layout);
 * - o corpo traz APENAS o texto da pergunta: empresa e usuário vêm da sessão no servidor;
 * - limite de mensagens por usuário para conter abusos.
 */
@RestController
@RequestMapping("/app/api/doki")
public class DokiController {

    static final int TAMANHO_MAXIMO = 500;
    static final int MENSAGENS_POR_MINUTO = 30;

    private final DokiService dokiService;
    private final LimitadorTaxa limitador;

    public DokiController(DokiService dokiService, LimitadorTaxa limitador) {
        this.dokiService = dokiService;
        this.limitador = limitador;
    }

    /** Corpo da requisição: somente a pergunta. Campos extras (ex.: empresaId) são ignorados. */
    public record Pergunta(String pergunta) {}

    @PostMapping(value = "/mensagens", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RespostaDoki> perguntar(@RequestBody(required = false) Pergunta corpo) {
        String texto = corpo == null || corpo.pergunta() == null ? "" : corpo.pergunta().trim();
        if (texto.isEmpty()) {
            return ResponseEntity.badRequest().body(aviso("INVALIDA", "Digite uma pergunta para eu poder ajudar. 🙂"));
        }
        if (texto.length() > TAMANHO_MAXIMO) {
            return ResponseEntity.badRequest().body(aviso("INVALIDA",
                    "Sua pergunta ficou um pouco longa. Pode resumir em até " + TAMANHO_MAXIMO + " caracteres?"));
        }
        if (!limitador.permitir("doki:" + SecurityUtils.usuario().getId(), MENSAGENS_POR_MINUTO, Duration.ofMinutes(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(aviso("LIMITE", "Muitas perguntas em pouco tempo! Aguarde um minutinho e tente novamente. ⏳"));
        }
        return ResponseEntity.ok(dokiService.responder(texto));
    }

    private static RespostaDoki aviso(String intencao, String texto) {
        return new RespostaDoki(intencao, texto, List.of(), List.of(), null, List.of(), List.of());
    }
}
