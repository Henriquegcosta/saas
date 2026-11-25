package br.com.vigentra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Tabela usada pelo Spring Security para o recurso "Lembrar acesso"
 * (PersistentTokenBasedRememberMeServices). Mapeada aqui apenas para que o
 * schema seja validado/gerado junto com as demais tabelas.
 */
@Entity
@Table(name = "persistent_logins")
public class PersistentLogin {

    @Id
    @Column(length = 64)
    private String series;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "last_used", nullable = false)
    private LocalDateTime lastUsed;

    public String getSeries() { return series; }
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public LocalDateTime getLastUsed() { return lastUsed; }
}
