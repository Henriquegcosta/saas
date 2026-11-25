package br.com.vigentra.service.comunicacao;

import br.com.vigentra.domain.enums.CanalEnvio;

import java.util.Map;

/** Retrato de uma integração para os painéis. "detalhes" contém apenas informações não sensíveis. */
public record StatusIntegracao(CanalEnvio canal, String provedor, EstadoIntegracao estado, String descricao,
                               Map<String, String> detalhes) {}
