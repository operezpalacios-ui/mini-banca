package com.mini_banca.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

/**
 * LAS RUTAS: qué petición va a dónde y con qué filtros. Se comprueban EN ORDEN (order) y gana
 * la primera que encaja. Todas acaban en el orquestador; cambian el limitador y el tiempo máximo.
 *
 *   1. POST /api/v1/auth/{login,registro,refresh}  -> limitador ESTRICTO (fuerza bruta)
 *   2. GET  /api/v1/cuentas/{id}/movimientos/export -> circuit breaker con 2+ minutos (CSV)
 *   3. Todo lo demás de /api/**                     -> limitador general
 *
 * La ruta NO se reescribe: /api/v1/cuentas llega al orquestador como /api/v1/cuentas.
 * Fíjate en que /actuator NO tiene ruta: las métricas del orquestador no salen por el gateway.
 */
@Configuration
public class RutasConfig {

    /** forward: = no sale de este proceso; lo contesta FallbackController. */
    private static final String FALLBACK = "forward:/fallback/orquestador";

    @Bean
    RouteLocator rutas(RouteLocatorBuilder rutas,
                       @Value("${orquestador.url}") String orquestador,
                       @Value("${orquestador.timeout-descargas}") Duration timeoutDescargas,
                       // @Qualifier OBLIGATORIO: limitadorApi es @Primary y, sin esto, Spring lo
                       // inyecta en LOS DOS parámetros (el login acabaría con el límite general).
                       @Qualifier("limitadorApi") RedisRateLimiter limitadorApi,
                       @Qualifier("limitadorAuth") RedisRateLimiter limitadorAuth,
                       KeyResolver ipDelCliente) {
        return rutas.routes()
                .route("auth", r -> r.order(0)
                        .method(HttpMethod.POST)
                        .and().path("/api/v1/auth/login", "/api/v1/auth/registro", "/api/v1/auth/refresh")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(limitadorAuth).setKeyResolver(ipDelCliente))
                                .circuitBreaker(c -> c.setName(CircuitBreakerConfig.NORMAL).setFallbackUri(FALLBACK)))
                        .uri(orquestador))

                .route("descargas", r -> r.order(1)
                        .path("/api/v1/cuentas/*/movimientos/export")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(limitadorApi).setKeyResolver(ipDelCliente))
                                .circuitBreaker(c -> c.setName(CircuitBreakerConfig.DESCARGAS).setFallbackUri(FALLBACK)))
                        // tiempo máximo esperando a que el orquestador EMPIECE a responder (en ms)
                        .metadata(RESPONSE_TIMEOUT_ATTR, timeoutDescargas.toMillis())
                        .uri(orquestador))

                .route("api", r -> r.order(2)
                        .path("/api/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(limitadorApi).setKeyResolver(ipDelCliente))
                                .circuitBreaker(c -> c.setName(CircuitBreakerConfig.NORMAL).setFallbackUri(FALLBACK)))
                        .uri(orquestador))
                .build();
    }
}
