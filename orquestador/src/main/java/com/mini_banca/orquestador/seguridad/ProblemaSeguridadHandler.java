package com.mini_banca.orquestador.seguridad;

import com.mini_banca.orquestador.error.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Security corta las peticiones ANTES de llegar a los controllers, así que el
 * GlobalExceptionHandler no se entera. Aquí damos a esos errores el mismo formato ProblemDetail.
 *   401 = "no sé quién eres"   (sin token, token falso o caducado)
 *   403 = "sé quién eres, pero no puedes" (un USER en /api/admin)
 */
@Component
public class ProblemaSeguridadHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        escribir(response, request, 401, "No autenticado",
                "Necesitas iniciar sesión: falta el token o no es válido o ha caducado");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escribir(response, request, 403, "Sin permiso", "No tienes permiso para acceder a este recurso");
    }

    private void escribir(HttpServletResponse response, HttpServletRequest request,
                          int status, String titulo, String detalle) throws IOException {
        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("title", titulo);
        problema.put("status", status);
        problema.put("detail", detalle);
        problema.put("instance", request.getRequestURI());
        problema.put("traceId", MDC.get(TraceIdFilter.MDC_KEY));

        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(problema));
    }
}
