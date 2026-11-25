package br.com.vigentra.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limitador de taxa em janela deslizante (memória). Usado para conter abusos:
 * reenvio de confirmação, recuperação de senha, convites e mensagens de teste
 * (cada envio pode gerar custo em provedores pagos).
 */
@Component
public class LimitadorTaxa {

    private final Map<String, Deque<Instant>> eventos = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimitadorTaxa(Clock clock) {
        this.clock = clock;
    }

    /** Registra uma tentativa e devolve false se a chave excedeu "maximo" eventos na "janela". */
    public boolean permitir(String chave, int maximo, Duration janela) {
        Deque<Instant> d = eventos.computeIfAbsent(chave, k -> new ConcurrentLinkedDeque<>());
        Instant limite = clock.instant().minus(janela);
        synchronized (d) {
            while (!d.isEmpty() && d.peekFirst().isBefore(limite)) d.pollFirst();
            if (d.size() >= maximo) return false;
            d.addLast(clock.instant());
            return true;
        }
    }

    /** Usado nos testes. */
    public void limpar() {
        eventos.clear();
    }
}
