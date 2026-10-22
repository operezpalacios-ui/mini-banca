package com.mini_banca.orquestador.cliente;

import com.mini_banca.orquestador.api.dto.*;
import com.mini_banca.orquestador.cliente.dto.RefreshTokenCore;
import com.mini_banca.orquestador.cliente.dto.RotacionCore;
import com.mini_banca.orquestador.cliente.dto.TotalCore;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final RestClient descargas;   // lección 9: mismo core, más paciencia (CSV)
    private final CircuitBreakerFactory<?, ?> circuitBreakers;

    public CoreClient(@Qualifier("coreRestClient") RestClient coreRestClient,
                      @Qualifier("coreRestClientDescargas") RestClient coreRestClientDescargas,
                      CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.rest = coreRestClient;
        this.descargas = coreRestClientDescargas;
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

    /**
     * Lección 9: con filtros (RF-4.2). Los valores van como VARIABLES de la URL ({texto}...):
     * así RestClient los codifica bien aunque lleven espacios, & o +.
     * Si los pegáramos tal cual, texto="a&b" partiría la URL en dos parámetros.
     */
    public Pagina<MovimientoDto> movimientos(Long usuarioId, Long cuentaId, FiltroMovimientos filtro,
                                             int page, int size) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> {
                    Map<String, Object> vars = new HashMap<>();
                    vars.put("id", cuentaId);
                    u.path("/internal/v1/cuentas/{id}/movimientos").queryParam("page", page).queryParam("size", size);
                    filtro.comoParametros().forEach((k, v) -> {
                        u.queryParam(k, "{" + k + "}");
                        vars.put(k, v);
                    });
                    return u.build(vars);
                })
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<MovimientoDto>>() {})));
    }

    /**
     * Lección 9 (RF-4.3): abre la descarga del CSV y devuelve la respuesta SIN LEERLA.
     *
     * exchange(..., false) = "no cierres la respuesta al salir": así el controller puede ir
     * copiando el CSV al navegador a medida que llega del core, sin guardarlo entero en memoria.
     * Antes miramos el código: si el core dice 404/422, lanzamos el error AHORA, cuando aún
     * podemos responder al navegador con un ProblemDetail normal.
     * ⚠️ Quien recibe la respuesta está obligado a cerrarla (try-with-resources).
     */
    public ClientHttpResponse abrirExportacion(Long usuarioId, Long cuentaId, FiltroMovimientos filtro) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> descargas.get()
                .uri(u -> {
                    Map<String, Object> vars = new HashMap<>();
                    vars.put("id", cuentaId);
                    u.path("/internal/v1/cuentas/{id}/movimientos/export");
                    filtro.comoParametros().forEach((k, v) -> {
                        u.queryParam(k, "{" + k + "}");
                        vars.put(k, v);
                    });
                    return u.build(vars);
                })
                .headers(ctx::aplicar)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        try (response) {
                            throw new CoreErrorException(response.getStatusCode(),
                                    CoreClientConfig.leerProblema(response.getBody().readAllBytes()));
                        }
                    }
                    return response;
                }, false));
    }

    public List<CuentaLigeraDto> cuentasLigeras(Long usuarioId) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri("/internal/v1/cuentas/ligeras")
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CuentaLigeraDto>>() {})));
    }

    public List<ResumenMensualDto> resumenMensual(Long usuarioId, Long cuentaId, int meses) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> u.path("/internal/v1/cuentas/{id}/resumen-mensual").queryParam("meses", meses).build(cuentaId))
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ResumenMensualDto>>() {})));
    }

    public Pagina<TransferenciaDto> misTransferencias(Long usuarioId, String estado, int page, int size) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> {
                    u.path("/internal/v1/transferencias").queryParam("page", page).queryParam("size", size);
                    if (estado != null) u.queryParam("estado", estado);
                    return u.build();
                })
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<TransferenciaDto>>() {})));
    }

    // ===================== ESCRITURAS (NO se reintentan a ciegas) =====================

    /** Lección 9 (RF-2.3). No se reintenta: es una escritura. */
    public CuentaDto cerrarCuenta(Long usuarioId, Long cuentaId) {
        Contexto ctx = Contexto.actual(usuarioId);
        return proteger(() -> rest.post()
                .uri("/internal/v1/cuentas/{id}/cerrar", cuentaId)
                .headers(ctx::aplicar)
                .retrieve()
                .body(CuentaDto.class));
    }

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

    // ===================== Lección 7: usuarios =====================

    public UsuarioDto registrarUsuario(RegistroRequest request) {
        Contexto ctx = Contexto.actual(null);
        return proteger(() -> rest.post()
                .uri("/internal/v1/usuarios")
                .headers(ctx::aplicar)
                .body(request)
                .retrieve()
                .body(UsuarioDto.class));
    }

    /** Si la contraseña no es válida, el core responde 401 y llega aquí como CoreErrorException. */
    public UsuarioDto verificarCredenciales(LoginRequest request) {
        Contexto ctx = Contexto.actual(null);
        return proteger(() -> rest.post()
                .uri("/internal/v1/auth/verificar")
                .headers(ctx::aplicar)
                .body(request)
                .retrieve()
                .body(UsuarioDto.class));
    }

    public Pagina<UsuarioDto> listarUsuarios(Long adminId, int page, int size) {
        Contexto ctx = Contexto.actual(adminId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> u.path("/internal/v1/usuarios").queryParam("page", page).queryParam("size", size).build())
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<UsuarioDto>>() {})));
    }

    // ===================== Lección 8: sesiones =====================

    public RefreshTokenCore crearRefreshToken(Long usuarioId) {
        Contexto ctx = Contexto.actual(null);
        return proteger(() -> rest.post()
                .uri("/internal/v1/auth/refresh-tokens")
                .headers(ctx::aplicar)
                .body(Map.of("usuarioId", usuarioId))
                .retrieve()
                .body(RefreshTokenCore.class));
    }

    public RotacionCore rotarRefreshToken(String refreshToken) {
        Contexto ctx = Contexto.actual(null);
        return proteger(() -> rest.post()
                .uri("/internal/v1/auth/refresh-tokens/rotar")
                .headers(ctx::aplicar)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .body(RotacionCore.class));
    }

    public void revocarRefreshToken(String refreshToken) {
        Contexto ctx = Contexto.actual(null);
        proteger(() -> rest.post()
                .uri("/internal/v1/auth/refresh-tokens/revocar")
                .headers(ctx::aplicar)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toBodilessEntity());
    }

    // ===================== Lección 8: saga y administración =====================

    public BigDecimal totalTransferidoHoy(Long usuarioId) {
        Contexto ctx = Contexto.actual(usuarioId);
        TotalCore total = proteger(() -> conReintento(() -> rest.get()
                .uri("/internal/v1/transferencias/total-hoy")
                .headers(ctx::aplicar)
                .retrieve()
                .body(TotalCore.class)));
        return total != null && total.total() != null ? total.total() : BigDecimal.ZERO;
    }

    public void auditar(Long usuarioId, String accion, String detalle) {
        Contexto ctx = Contexto.actual(usuarioId);
        java.util.Map<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put("usuarioId", usuarioId);
        cuerpo.put("accion", accion);
        cuerpo.put("detalle", detalle);
        proteger(() -> rest.post()
                .uri("/internal/v1/auditoria")
                .headers(ctx::aplicar)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity());
    }

    public UsuarioDto cambiarBloqueo(Long adminId, Long usuarioId, boolean bloquear) {
        Contexto ctx = Contexto.actual(adminId);
        return proteger(() -> rest.put()
                .uri("/internal/v1/usuarios/{id}/bloqueo", usuarioId)
                .headers(ctx::aplicar)
                .body(Map.of("bloqueado", bloquear))
                .retrieve()
                .body(UsuarioDto.class));
    }

    public Pagina<AuditoriaDto> buscarAuditoria(Long adminId, Long usuarioId, String accion,
                                                String desde, String hasta, int page, int size) {
        Contexto ctx = Contexto.actual(adminId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> {
                    u.path("/internal/v1/auditoria").queryParam("page", page).queryParam("size", size);
                    if (usuarioId != null) u.queryParam("usuarioId", usuarioId);
                    if (accion != null) u.queryParam("accion", accion);
                    if (desde != null) u.queryParam("desde", desde);
                    if (hasta != null) u.queryParam("hasta", hasta);
                    return u.build();
                })
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<AuditoriaDto>>() {})));
    }

    // ===================== Lección 9: administración =====================

    public Pagina<TransferenciaDto> buscarTransferencias(Long adminId, Long usuarioId, String estado,
                                                         int page, int size) {
        Contexto ctx = Contexto.actual(adminId);
        return proteger(() -> conReintento(() -> rest.get()
                .uri(u -> {
                    u.path("/internal/v1/transferencias/admin").queryParam("page", page).queryParam("size", size);
                    if (usuarioId != null) u.queryParam("usuarioId", usuarioId);
                    if (estado != null) u.queryParam("estado", estado);
                    return u.build();
                })
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<TransferenciaDto>>() {})));
    }

    public TransferenciaDto anularTransferencia(Long adminId, Long transferenciaId, AnulacionRequest request) {
        Contexto ctx = Contexto.actual(adminId);
        return proteger(() -> rest.post()
                .uri("/internal/v1/transferencias/{id}/anulacion", transferenciaId)
                .headers(ctx::aplicar)
                .body(request)
                .retrieve()
                .body(TransferenciaDto.class));
    }

    public Map<String, Object> demoAislamiento(Long adminId, String nivel) {
        Contexto ctx = Contexto.actual(adminId);
        return proteger(() -> rest.post()
                .uri(u -> u.path("/internal/v1/demo/aislamiento").queryParam("nivel", nivel).build())
                .headers(ctx::aplicar)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
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
            if (usuarioId != null) {
                // El id sale del JWT ya validado. El core sigue sin poder comprobarlo por sí mismo:
                // en la lección 8 añadiremos seguridad servicio a servicio (RF-0.3).
                h.set("X-User-Id", String.valueOf(usuarioId));
            }
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
