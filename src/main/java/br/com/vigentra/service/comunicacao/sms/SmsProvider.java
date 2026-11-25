package br.com.vigentra.service.comunicacao.sms;

import br.com.vigentra.service.comunicacao.ResultadoEnvio;

/**
 * Ponto de extensão para um gateway de SMS (ex.: Zenvia, Twilio, Amazon SNS).
 * Para integrar: criar um @Component que implemente esta interface, retornar seu
 * identificador em {@link #nome()} e selecioná-lo com SMS_PROVIDER=<nome>.
 * Passo a passo em docs/NOTIFICACOES.md.
 */
public interface SmsProvider {

    /** Identificador usado em SMS_PROVIDER. */
    String nome();

    /** @param para celular em E.164; @param texto até 160 caracteres (GSM-7) */
    ResultadoEnvio enviar(String para, String texto);
}
