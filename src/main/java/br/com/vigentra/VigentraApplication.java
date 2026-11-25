package br.com.vigentra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * VIGENTRA - Gestor de Validade de Documentos e Certificados.
 * Ponto de entrada da aplicação Spring Boot.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class VigentraApplication {

    public static void main(String[] args) {
        // Fuso padrão da JVM alinhado ao negócio (datas de vencimento são brasileiras),
        // independentemente do fuso configurado na VM (geralmente UTC).
        TimeZone.setDefault(TimeZone.getTimeZone(System.getenv().getOrDefault("APP_TIMEZONE", "America/Sao_Paulo")));
        SpringApplication.run(VigentraApplication.class, args);
    }
}
