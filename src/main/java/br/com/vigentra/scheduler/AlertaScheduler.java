package br.com.vigentra.scheduler;

import br.com.vigentra.service.alerta.AlertaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Agendamentos da rotina de alertas.
 * - Verificação diária (padrão 07:00, fuso America/Sao_Paulo) - ALERTS_CRON;
 * - Retentativa de envios com falha (padrão: minuto 15 de cada hora) - ALERTS_RETRY_CRON.
 * Cada execução assume uma trava no banco: com várias instâncias, só uma processa.
 * Pode ser desligada com ALERTS_ENABLED=false (ex.: nos testes automatizados).
 */
@Component
@ConditionalOnProperty(prefix = "app.alerts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertaScheduler.class);

    private final AlertaService alertaService;
    private final BloqueioAgendadorService bloqueio;

    public AlertaScheduler(AlertaService alertaService, BloqueioAgendadorService bloqueio) {
        this.alertaService = alertaService;
        this.bloqueio = bloqueio;
    }

    @Scheduled(cron = "${app.alerts.cron}", zone = "${app.timezone}")
    public void verificacaoDiaria() {
        log.info("Iniciando verificação diária de vencimentos...");
        bloqueio.executarComTrava("alertas-diarios", Duration.ofHours(2), alertaService::executarTodas);
    }

    @Scheduled(cron = "${app.alerts.retry-cron}", zone = "${app.timezone}")
    public void retentativas() {
        Integer ok = bloqueio.executarComTrava("alertas-retentativas", Duration.ofMinutes(30), alertaService::reprocessarFalhas);
        if (ok != null && ok > 0) log.info("Retentativa de envios: {} mensagem(ns) concluída(s).", ok);
    }
}
