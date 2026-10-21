package com.mini_banca.orquestador.cliente;

import com.mini_banca.orquestador.api.dto.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Cliente TIPADO del core (RF-0.2): el resto del orquestador llama a métodos Java
 * normales y no sabe nada de URLs, cabeceras, timeouts ni circuit breakers.
 */
@Component
public class CoreClient {

    private static final Logger log = LoggerFactory.getLogger(CoreClient.class);

    private final RestClient rest;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;

    public CoreClient(RestClient coreRestClient, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.rest = coreRestClient;
        this.circuitBreakers = circuitBreakers;
    }

    // ===================== LECTURAS (idempotentes: se pueden reintentar) =====================

    public Pagina<CuentaDto> listarCuentas(Long usuarioId, int page, int size, String sort) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> {
                    u.path("/internal/v1/cuentas").queryParam("page", page).queryParam("size", size);
                    if (sort != null) u.queryParam("sort", sort);
                    return u.build();
                })
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<CuentaDto>>() {})));
    }

    public CuentaDto detalleCuenta(Long usuarioId, Long cuentaId) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri("/internal/v1/cuentas/{id}", cuentaId)
                .headers(ctx::aplicar)
                .retrieve()
                .body(CuentaDto.class)));
    }

    public Pagina<MovimientoDto> movimientos(Long usuarioId, Long cuentaId, int page, int size) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> u.path("/internal/v1/cuentas/{id}/movimientos")
                        .queryParam("page", page).queryParam("size", size).build(cuentaId))
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<MovimientoDto>>() {})));
    }

    // ===================== ESCRITURAS (NO se reintentan a ciegas) =====================

    public CuentaDto crearCuenta(Long usuarioId, CrearCuentaRequest request) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> rest.post()
                .uri("/internal/v1/cuentas")
                .headers(ctx::aplicar)
                .body(request)
                .retrieve()
                .body(CuentaDto.class));
    }

    /** Devuelve la respuesta entera para poder reenviar la cabecera Idempotent-Replayed. */
    public ResponseEntity<TransferenciaDto> transferir(Long usuarioId, String idempotencyKey,
                                                       TransferenciaRequest request) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> rest.post()
                .uri("/internal/v1/transferencias")
                .headers(h -> {
                    ctx.aplicar(h);
                    h.set("Idempotency-Key", idempotencyKey);   // el orquestador la EXIGE y la REENVÍA
                })
                .body(request)
                .retrieve()
                .toEntity(TransferenciaDto.class));
    }

    // ===================== Resiliencia =====================

    /** Toda llamada pasa por el circuit breaker "core". Si falla, traducirFallo decide el código HTTP. */
    private <T> T proteger(Supplier<T> llamada) {
        return circuitBreakers.create("core").run(llamada, this::traducirFallo);
    }

    /**
     * Reintento SOLO para lecturas y SOLO si falló la red (no si el core respondió un error,
     * ni si tardó demasiado). Leer dos veces no hace daño; transferir dos veces, sí.
     */
    private <T> T conReintento(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            if (buscarCausa(e, HttpTimeoutException.class) != null) {
                throw e;   // timeout: reintentar solo duplicaría la espera
            }
            log.warn("Fallo de red llamando al core, reintento una vez: {}", e.getMessage());
            esperar(200);
            return llamada.get();
        }
    }

    private <T> T traducirFallo(Throwable fallo) {
        Throwable e = desenvolver(fallo);

        if (e instanceof CoreErrorException errorDelCore) {
            throw errorDelCore;   // el core respondió: el GlobalExceptionHandler lo reenvía
        }
        if (e instanceof CallNotPermittedException) {
            throw new ComunicacionCoreException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                    "El servicio está temporalmente no disponible. Inténtalo de nuevo en unos segundos.", e);
        }
        if (e instanceof TimeoutException || (buscarCausa(e, HttpTimeoutException.class) != null
                && buscarCausa(e, HttpConnectTimeoutException.class) == null)) {
            throw new ComunicacionCoreException(HttpStatus.GATEWAY_TIMEOUT, "Tiempo de espera agotado",
                    "El servicio interno ha tardado demasiado en responder.", e);
        }
        if (e instanceof ResourceAccessException) {
            // No hemos podido ni conectar (core apagado, red caída...)
            log.error("No se puede conectar con el core: {}", e.getMessage());
            throw new ComunicacionCoreException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                    "El servicio interno no está disponible en este momento.", e);
        }
        if (e instanceof RestClientException) {
            // Respondió, pero con algo que no entendemos (JSON inesperado...)
            log.error("Respuesta inválida del core", e);
            throw new ComunicacionCoreException(HttpStatus.BAD_GATEWAY, "Respuesta inválida",
                    "El servicio interno ha devuelto una respuesta no válida.", e);
        }
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException(e);
    }

    // ===================== Utilidades =====================

    /**
     * Lo que el core necesita saber de cada petición. Se captura en el hilo de la petición
     * ANTES de entrar en el circuit breaker, porque el circuit breaker ejecuta la llamada
     * en otro hilo, y el traceId (MDC) no viaja solo entre hilos.
     */
    private record Contexto(Long usuarioId, String traceId) {
        static Contexto actual(Long usuarioId) {
            return new Contexto(usuarioId, MDC.get("traceId"));
        }

        void aplicar(HttpHeaders h) {
            h.set("X-User-Id", String.valueOf(usuarioId));   // provisional hasta la lección 7 (JWT)
            if (traceId != null) {
                h.set("X-Trace-Id", traceId);                // el MISMO traceId en los dos servicios
            }
        }
    }

    private static Throwable desenvolver(Throwable t) {
        while ((t instanceof ExecutionException || t instanceof CompletionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static <X extends Throwable> X buscarCausa(Throwable t, Class<X> tipo) {
        while (t != null) {
            if (tipo.isInstance(t)) return tipo.cast(t);
            t = t.getCause();
        }
        return null;
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
