package com.minibanca.core.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
 * UN ÚNICO sitio que convierte excepciones en respuestas HTTP (RF-8.1),
 * todas con el formato estándar RFC 7807 / ProblemDetail (RF-8.2):
 *
 *   { "type", "title", "status", "detail", "instance", "traceId", ... }
 *
 * Al extender ResponseEntityExceptionHandler, Spring ya convierte a ProblemDetail
 * sus propios errores (JSON mal formado, cabecera que falta, ruta inexistente...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ----- Nuestras excepciones de dominio -----

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), req);
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    ProblemDetail saldoInsuficiente(SaldoInsuficienteException ex, HttpServletRequest req) {
        return problema(HttpStatusCode.valueOf(422), "Saldo insuficiente", ex.getMessage(), req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail reglaNegocio(ReglaNegocioException ex, HttpServletRequest req) {
        return problema(HttpStatusCode.valueOf(422), "Regla de negocio incumplida", ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail conflicto(ConflictoException ex, HttpServletRequest req) {
        return problema(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), req);
    }

    // ----- Excepciones técnicas que también tienen sentido para el cliente -----

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrencia(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return problema(HttpStatus.CONFLICT, "Conflicto de concurrencia",
                "La cuenta ha cambiado mientras operabas. Vuelve a intentarlo.", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        // El mensaje real lleva SQL y nombres de tablas: va al LOG, nunca al cliente.
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflicto",
                "La operación entra en conflicto con datos existentes", req);
    }

    // ----- Red de seguridad: cualquier otra cosa -> 500 genérico -----

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado", ex);   // la traza completa, SOLO en el log
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ha ocurrido un error inesperado. Si persiste, contacta con soporte indicando el traceId.", req);
    }

    // ----- Errores de validación: añadimos la lista de campos (RF-8.3) -----

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

    // Todos los ProblemDetail que genera Spring pasan por aquí: les ponemos instance + traceId
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd && request instanceof ServletWebRequest swr) {
            completar(pd, swr.getRequest());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    // ----- Utilidades -----

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
}
