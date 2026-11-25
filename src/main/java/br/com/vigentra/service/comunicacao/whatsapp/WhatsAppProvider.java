package br.com.vigentra.service.comunicacao.whatsapp;

import br.com.vigentra.service.comunicacao.ResultadoEnvio;

/** Provedor do canal WhatsApp. Implementações: Cloud API oficial da Meta e desenvolvimento (disco). */
public interface WhatsAppProvider {

    String nome();

    ResultadoEnvio enviar(MensagemWhatsApp m);
}
