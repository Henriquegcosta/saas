package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.CanalEnvio;

/**
 * Contrato comum dos canais (e-mail, WhatsApp, SMS). Cada canal escolhe internamente o provedor
 * conforme as variáveis de ambiente; quem envia mensagens não conhece o provedor.
 * Para adicionar um canal novo: novo valor em {@link CanalEnvio} + uma implementação desta interface.
 */
public interface CanalMensageria {

    CanalEnvio canal();

    StatusIntegracao status();

    default boolean utilizavel() {
        return status().estado().utilizavel();
    }
}
