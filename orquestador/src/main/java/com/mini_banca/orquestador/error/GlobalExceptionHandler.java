package com.mini_banca.orquestador.error;

import com.mini_banca.orquestador.cliente.ComunicacionCoreException;
import com.mini_banca.orquestador.cliente.CoreErrorException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Traducción de errores del orquestador (RF-8.6):
 *  - El core respondió 4xx  -> reenviamos SU ProblemDetail (mismo status, title, detail, errores).
 *  - El core respondió 5xx  -> 502, sin detalles internos.
 *  - No pudimos hablar con él -> 503 / 504.
 * El campo "instance" del core (/internal/v1/...) NO se reenvía: es una ruta interna.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CoreErrorException.class)
    ProblemDetail errorDelCore(CoreErrorException ex, HttpServletRequest req) {
        if (ex.esErrorDelCliente()) {
            Map<String, Object> p = ex.getProblema();
            ProblemDetail pd = ProblemDetail.forStatus(ex.getStatus());
            pd.setTitle(texto(p.get("title"), "Error"));
            pd.setDetail(texto(p.get("detail"), null));
            if (p.get("errores") != null) {
                pd.setProperty("errores", p.get("errores"));
            }
            completar(pd, req);
            return pd;
        }
        log.error("El core respondió {}: {}", ex.getStatus().value(), ex.getProblema());
        return problema(HttpStatus.BAD_GATEWAY, "Error en el servicio interno",
                "El servicio interno ha fallado. Inténtalo más tarde.", req);
    }

    @ExceptionHandler(ComunicacionCoreException.class)
    ProblemDetail comunicacion(ComunicacionCoreException ex, HttpServletRequest req) {
        return problema(ex.getStatus(), ex.getTitulo(), ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ha ocurrido un error inesperado. Si persiste, contacta con soporte indicando el traceId.", req);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setTitle("Datos no válidos");
        body.setDetail("Revisa los campos marcados");
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "campo", fe.getField(),
                        "mensaje", Objects.requireNonNullElse(fe.getDefaultMessage(), "no válido")))
                .toList();
        body.setProperty("errores", errores);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd && request instanceof ServletWebRequest swr) {
            completar(pd, swr.getRequest());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ProblemDetail problema(HttpStatusCode status, String titulo, String detalle, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setTitle(titulo);
        completar(pd, req);
        return pd;
    }

    private void completar(ProblemDetail pd, HttpServletRequest req) {
        if (pd.getInstance() == null) {
            pd.setInstance(URI.create(req.getRequestURI()));
        }
        pd.setProperty("traceId", MDC.get(TraceIdFilter.MDC_KEY));
    }

    private static String texto(Object valor, String porDefecto) {
        return valor != null ? valor.toString() : porDefecto;
    }
}
