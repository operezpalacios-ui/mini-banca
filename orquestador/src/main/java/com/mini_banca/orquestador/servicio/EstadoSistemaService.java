package com.mini_banca.orquestador.servicio;

import com.mini_banca.orquestador.cliente.CoreClient;
import com.mini_banca.orquestador.cliente.EstadoCircuito;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Lección 13: un "cuadro de mando" para el administrador, juntando en UNA respuesta:
 *  - la SALUD del core (y, dentro, la de su base de datos y su Redis),
 *  - el estado del circuit breaker,
 *  - las métricas de negocio de transferencias.
 * Lo mismo se puede ver por separado en /actuator/..., pero así lo pinta el panel de Angular.
 */
@Service
public class EstadoSistemaService {

    private final CoreClient core;
    private final EstadoCircuito circuito;
    private final MeterRegistry metricas;

    public EstadoSistemaService(CoreClient core, EstadoCircuito circuito, MeterRegistry metricas) {
        this.core = core;
        this.circuito = circuito;
        this.metricas = metricas;
    }

    public Map<String, Object> estado() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("momento", Instant.now());
        r.put("orquestador", Map.of("status", "UP"));   // si estás leyendo esto, el orquestador responde
        r.put("core", core.saludCore());
        r.put("circuitBreakerCore", circuito.resumen());
        r.put("transferencias", resumenTransferencias());
        return r;
    }

    private Map<String, Object> resumenTransferencias() {
        Map<String, Object> porResultado = new TreeMap<>();
        long total = 0;
        double sumaMs = 0;
        for (Timer t : metricas.find(TransferenciaSagaService.METRICA).timers()) {
            long n = t.count();
            porResultado.put(t.getId().getTag("resultado"), n);
            total += n;
            sumaMs += t.totalTime(TimeUnit.MILLISECONDS);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("porResultado", porResultado);
        r.put("tiempoMedioMs", total == 0 ? 0 : Math.round(sumaMs / total));
        r.put("nota", "Contadores desde que arrancó el orquestador");
        return r;
    }
}
