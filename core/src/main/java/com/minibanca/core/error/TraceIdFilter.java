package com.minibanca.core.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Da a cada petición un "número de expediente" (traceId) (RF-8.2, RF-10.4).
 * - Se guarda en el MDC: así aparece en TODAS las líneas de log de esa petición.
 * - Se devuelve en la cabecera X-Trace-Id y dentro de los errores.
 * Si el cliente (más adelante, el orquestador) ya trae uno, lo reutilizamos.
 *
 * (En la lección de observabilidad lo sustituiremos por Micrometer Tracing.)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String recibido = request.getHeader(CABECERA);
        String traceId = (recibido != null && recibido.matches("[A-Za-z0-9-]{8,64}"))
                ? recibido
                : UUID.randomUUID().toString().replace("-", "");

        MDC.put(MDC_KEY, traceId);
        response.setHeader(CABECERA, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);   // los hilos se reutilizan: hay que limpiar siempre
        }
    }
}
