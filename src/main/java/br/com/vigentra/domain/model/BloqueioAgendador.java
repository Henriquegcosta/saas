package br.com.vigentra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Trava distribuída das rotinas agendadas: garante que, mesmo com várias instâncias
 * da aplicação (ou um agendamento sobreposto), a rotina rode em apenas uma delas por vez.
 */
@Entity
@Table(name = "bloqueios_agendador")
public class BloqueioAgendador {

    @Id
    @Column(length = 64)
    private String nome;

    @Column(name = "bloqueado_ate", nullable = false)
    private LocalDateTime bloqueadoAte;

    @Column(name = "bloqueado_em", nullable = false)
    private LocalDateTime bloqueadoEm;

    @Column(name = "bloqueado_por", nullable = false, length = 255)
    private String bloqueadoPor;

    public BloqueioAgendador() {}

    public BloqueioAgendador(String nome, LocalDateTime bloqueadoAte, LocalDateTime bloqueadoEm, String bloqueadoPor) {
        this.nome = nome;
        this.bloqueadoAte = bloqueadoAte;
        this.bloqueadoEm = bloqueadoEm;
        this.bloqueadoPor = bloqueadoPor;
    }

    public String getNome() { return nome; }
    public LocalDateTime getBloqueadoAte() { return bloqueadoAte; }
    public LocalDateTime getBloqueadoEm() { return bloqueadoEm; }
    public String getBloqueadoPor() { return bloqueadoPor; }
}
