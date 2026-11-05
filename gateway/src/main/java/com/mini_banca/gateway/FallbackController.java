package com.mini_banca.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.concurrent.TimeoutException;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

/**
 * Lo que responde el gateway cuando NO puede hablar con el orquestador (caído, colgado o con el
 * circuit breaker abierto). Mismo formato de error que el resto de la API (ProblemDetail + traceId),
 * así el frontend lo enseña igual que cualquier otro error.
 *  - 504 si el orquestador tardó demasiado.
 *  - 503 en cualquier otro caso (no se pudo conectar, circuito abierto...).
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /** Sin "method": llegan GET, POST, PUT... (es la MISMA petición, reenviada aquí). */
    @RequestMapping("/fallback/orquestador")
    ResponseEntity<ProblemDetail> orquestadorNoDisponible(ServerWebExchange exchange) {
        Throwable causa = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        boolean tardo = causa instanceof TimeoutException;
        HttpStatus status = tardo ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        String traceId = TraceIdFilter.de(exchange);

        log.warn("Orquestador no disponible [traceId={}] {} {}: {}", traceId,
                exchange.getRequest().getMethod(), rutaOriginal(exchange),
                causa != null ? causa.toString() : "circuito abierto");

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, tardo
                ? "El servicio está tardando demasiado en responder. Inténtalo de nuevo en unos segundos."
                : "El servicio no está disponible en este momento. Inténtalo de nuevo en unos segundos.");
        pd.setTitle(tardo ? "Tiempo de espera agotado" : "Servicio no disponible");
        pd.setProperty("traceId", traceId);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    private static String rutaOriginal(ServerWebExchange exchange) {
        java.util.Set<java.net.URI> urls = exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        return urls != null && !urls.isEmpty()
                ? urls.iterator().next().getPath()
                : exchange.getRequest().getPath().value();
    }
}
