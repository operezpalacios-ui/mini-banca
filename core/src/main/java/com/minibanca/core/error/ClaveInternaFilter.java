package com.minibanca.core.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SEGURIDAD SERVICIO A SERVICIO (RF-0.3).
 * Toda petición a /internal/** debe traer la cabecera X-Internal-Key con la clave que solo
 * conocen el core y el orquestador. Así, aunque alguien llegara a la red interna, no podría
 * llamar al core haciéndose pasar por el orquestador (ni inventarse un X-User-Id).
 *
 * (Una alternativa más robusta es mTLS: certificados en los dos lados. Esta es la versión sencilla.)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)   // justo después del TraceIdFilter
public class ClaveInternaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClaveInternaFilter.class);
    public static final String CABECERA = "X-Internal-Key";

    private final byte[] claveEsperada;

    public ClaveInternaFilter(@Value("${internal.api-key}") String clave) {
        // Fallar al ARRANCAR es mucho mejor que arrancar "abierto" sin protección (RF-11.4)
        if (clave == null || clave.length() < 32) {
            throw new IllegalStateException(
                    "Falta INTERNAL_API_KEY (mínimo 32 caracteres). Añádela al .env (instrucciones de la lección 8).");
        }
        this.claveEsperada = clave.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");   // /actuator/health sigue abierto
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String recibida = request.getHeader(CABECERA);
        // MessageDigest.isEqual compara en TIEMPO CONSTANTE: con equals() normal, un atacante
        // podría adivinar la clave letra a letra midiendo cuánto tarda la comparación.
        if (recibida == null || !MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Llamada a {} rechazada: falta X-Internal-Key o no es válida", request.getRequestURI());
            Map<String, Object> problema = new LinkedHashMap<>();
            problema.put("title", "No autorizado");
            problema.put("status", 401);
            problema.put("detail", "Este servicio es interno y solo acepta peticiones del orquestador");
            problema.put("instance", request.getRequestURI());
            problema.put("traceId", MDC.get(TraceIdFilter.MDC_KEY));
            response.setStatus(401);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JsonMapper.builder().build().writeValueAsString(problema));
            return;
        }
        chain.doFilter(request, response);
    }
}
