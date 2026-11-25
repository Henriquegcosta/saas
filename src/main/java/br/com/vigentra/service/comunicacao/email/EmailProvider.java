package br.com.vigentra.service.comunicacao.email;

import br.com.vigentra.service.comunicacao.ResultadoEnvio;

/** Provedor de envio de e-mail. Implementações: Brevo (API), SMTP e desenvolvimento (disco). */
public interface EmailProvider {

    /** Identificador curto gravado no histórico (ex.: "brevo"). */
    String nome();

    /** Possui as credenciais/configuração necessárias? */
    boolean configurado();

    ResultadoEnvio enviar(MensagemEmail m);
}
