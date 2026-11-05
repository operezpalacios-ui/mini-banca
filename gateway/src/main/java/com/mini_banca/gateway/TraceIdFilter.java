package com.mini_banca.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * El "número de expediente" (traceId) nace ahora en el GATEWAY, la primera capa Java.
 *  - Si la petición ya trae un X-Trace-Id válido, se respeta; si no, se genera uno.
 *  - Se le pasa al orquestador en la cabecera X-Trace-Id (su TraceIdFilter ya lo acepta y lo
 *    sigue pasando al core): la MISMA petición tiene el mismo traceId en los tres servicios.
 *  - Se devuelve al navegador en X-Trace-Id, incluso si el orquestador está caído (fallback).
 *
 * Mismo formato que en el orquestador: letras, números y guiones, de 8 a 64 caracteres.
 * Así nadie puede colar en los logs un texto arbitrario (saltos de línea, etc.).
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String CABECERA = "X-Trace-Id";
    /** Atributo del exchange: para que el fallback pueda poner el traceId en el error. */
    public static final String ATRIBUTO = TraceIdFilter.class.getName() + ".traceId";

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String recibido = exchange.getRequest().getHeaders().getFirst(CABECERA);
        String traceId = (recibido != null && VALIDO.matcher(recibido).matches())
                ? recibido
                : UUID.randomUUID().toString().replace("-", "");

        exchange.getAttributes().put(ATRIBUTO, traceId);
        // Justo ANTES de enviar la respuesta (sea del orquestador, un 429 o el fallback): "set",
        // no "add". El orquestador también devuelve X-Trace-Id y el gateway copia sus cabeceras;
        // así el navegador ve UNA sola cabecera y no dos iguales.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CABECERA, traceId);
            return Mono.empty();
        });

        ServerWebExchange conTraza = exchange.mutate()
                .request(r -> r.headers(h -> h.set(CABECERA, traceId)))
                .build();
        return chain.filter(conTraza);
    }

    /** Lo primero de todo: hasta un 429 del limitador debe llevar su traceId. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    public static String de(ServerWebExchange exchange) {
        return exchange.getAttribute(ATRIBUTO);
    }
}
