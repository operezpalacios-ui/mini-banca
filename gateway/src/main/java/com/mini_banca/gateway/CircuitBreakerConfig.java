package com.mini_banca.gateway;

import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * CIRCUIT BREAKER delante del orquestador (el mismo "diferencial" que el orquestador tiene
 * delante del core, ahora una capa más arriba):
 *  - Últimas 10 llamadas; si (con al menos 5) falla el 50 % o más -> se ABRE.
 *  - ABIERTO: durante 15 s el gateway ni intenta llamar; responde 503 al instante (FallbackController).
 *  - MEDIO ABIERTO: 2 llamadas de prueba; si van bien, se cierra.
 *
 * Qué cuenta como FALLO: no poder conectar o pasarse de tiempo. Un 4xx o un 5xx que DEVUELVE el
 * orquestador no cuenta: el orquestador está vivo y su respuesta (ProblemDetail) llega tal cual.
 *
 * Dos "diferenciales" con distinto límite de tiempo:
 *  - "orquestador":           peticiones normales.
 *  - "orquestador-descargas": exportación CSV (lección 9), que puede tardar hasta 2 minutos.
 */
@Configuration
public class CircuitBreakerConfig {

    public static final String NORMAL = "orquestador";
    public static final String DESCARGAS = "orquestador-descargas";

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakersOrquestador(
            @Value("${orquestador.timeout}") Duration timeout,
            @Value("${orquestador.timeout-descargas}") Duration timeoutDescargas) {
        return factory -> {
            factory.configure(b -> b.circuitBreakerConfig(reglas()).timeLimiterConfig(limite(timeout)), NORMAL);
            factory.configure(b -> b.circuitBreakerConfig(reglas()).timeLimiterConfig(limite(timeoutDescargas)), DESCARGAS);
            factory.addCircuitBreakerCustomizer(cb -> cb.getEventPublisher().onStateTransition(e ->
                    log.warn("CIRCUIT BREAKER '{}': {}", e.getCircuitBreakerName(), e.getStateTransition())),
                    NORMAL, DESCARGAS);
        };
    }

    private static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig reglas() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
    }

    private static TimeLimiterConfig limite(Duration timeout) {
        return TimeLimiterConfig.custom().timeoutDuration(timeout).build();
    }
}
