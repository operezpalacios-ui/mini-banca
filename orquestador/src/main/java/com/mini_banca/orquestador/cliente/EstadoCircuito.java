package com.mini_banca.orquestador.cliente;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lección 13: guarda una referencia al circuit breaker "core" para poder ENSEÑAR su estado
 * (CLOSED = todo normal, OPEN = el core está fallando y no le llamamos, HALF_OPEN = probando).
 * El circuit breaker se crea con la primera llamada al core; antes, aún no existe.
 */
@Component
public class EstadoCircuito {

    private volatile CircuitBreaker circuito;

    void registrar(CircuitBreaker cb) {
        this.circuito = cb;
    }

    public Map<String, Object> resumen() {
        Map<String, Object> r = new LinkedHashMap<>();
        CircuitBreaker cb = this.circuito;
        if (cb == null) {
            r.put("estado", "CLOSED");
            r.put("nota", "Aún no se ha llamado al core");
            return r;
        }
        CircuitBreaker.Metrics m = cb.getMetrics();
        r.put("estado", cb.getState().name());
        r.put("llamadasRecientes", m.getNumberOfBufferedCalls());
        r.put("fallidas", m.getNumberOfFailedCalls());
        r.put("porcentajeFallos", m.getFailureRate() < 0 ? 0 : m.getFailureRate());   // -1 = aún no hay datos suficientes
        r.put("rechazadasSinLlamar", m.getNumberOfNotPermittedCalls());
        return r;
    }
}
