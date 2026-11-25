package br.com.vigentra.web.controller;

import br.com.vigentra.service.comunicacao.whatsapp.WhatsAppWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook do WhatsApp Cloud API (configurado no painel da Meta apontando para
 * https://SEU-DOMINIO/webhooks/whatsapp). Sem token CSRF: a autenticidade é garantida
 * pela assinatura HMAC X-Hub-Signature-256 calculada com o App Secret.
 */
@RestController
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppWebhookService service;

    public WhatsAppWebhookController(WhatsAppWebhookService service) {
        this.service = service;
    }

    /** Verificação feita pela Meta ao cadastrar o webhook. */
    @GetMapping("/webhooks/whatsapp")
    public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode", required = false) String modo,
                                            @RequestParam(name = "hub.verify_token", required = false) String token,
                                            @RequestParam(name = "hub.challenge", required = false) String desafio) {
        if (service.verificacaoValida(modo, token) && desafio != null) return ResponseEntity.ok(desafio);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/webhooks/whatsapp")
    public ResponseEntity<Void> receber(@RequestBody byte[] corpo,
                                        @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura) {
        if (!service.assinaturaValida(corpo, assinatura)) {
            log.warn("Webhook do WhatsApp recusado: assinatura ausente ou inválida.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        service.processar(corpo);
        return ResponseEntity.ok().build();
    }
}
