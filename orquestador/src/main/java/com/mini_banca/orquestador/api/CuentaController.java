package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.*;
import com.mini_banca.orquestador.cliente.CoreClient;
import com.mini_banca.orquestador.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * API PÚBLICA (/api/v1): es lo que llamará el navegador.
 * El orquestador valida, añade contexto (usuario, traceId) y delega en el core.
 *
 * Lección 7: el usuario ya NO viene de una cabecera que el navegador pueda inventarse,
 * sino del token JWT, que Spring Security ha validado antes de llegar aquí.
 */
@Tag(name = "Cuentas", description = "Cuentas del usuario autenticado y sus movimientos")
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private static final Logger log = LoggerFactory.getLogger(CuentaController.class);

    private final CoreClient core;

    public CuentaController(CoreClient core) {
        this.core = core;
    }

    @GetMapping
    public Pagina<CuentaDto> listar(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String sort) {
        return core.listarCuentas(UsuarioActual.id(jwt), page, size, sort);
    }

    @GetMapping("/{id}")
    public CuentaDto detalle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return core.detalleCuenta(UsuarioActual.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaDto crear(@AuthenticationPrincipal Jwt jwt,
                           @Valid @RequestBody CrearCuentaRequest request) {
        return core.crearCuenta(UsuarioActual.id(jwt), request);
    }

    /**
     * Lección 9: con filtros opcionales (RF-4.2). Spring rellena el record FiltroMovimientos con los
     * parámetros de la URL que se llamen igual: ?desde=2026-10-01&tipo=CARGO&texto=luz ...
     * (@ParameterObject solo sirve para que Swagger los muestre como parámetros sueltos).
     */
    @Operation(summary = "Histórico de movimientos, con filtros opcionales")
    @GetMapping("/{id}/movimientos")
    public Pagina<MovimientoDto> movimientos(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable Long id,
                                             @ParameterObject FiltroMovimientos filtro,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return core.movimientos(UsuarioActual.id(jwt), id, filtro, page, size);
    }

    // ---------- Lección 9 ----------

    /**
     * RF-4.3: descarga CSV en STREAMING de punta a punta:
     *    BBDD --(lotes de 500)--> core --(bytes)--> orquestador --(bytes)--> navegador
     * Ninguno de los tres tiene el fichero entero en memoria; el orquestador solo "trasvasa".
     */
    @Operation(summary = "Exportar movimientos a CSV (acepta los mismos filtros que el histórico)")
    @GetMapping(value = "/{id}/movimientos/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportar(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable Long id,
                                                          @ParameterObject FiltroMovimientos filtro) {
        // Si la cuenta no es tuya o los filtros están mal, salta AQUÍ (404/422 normales)
        ClientHttpResponse respuestaCore = core.abrirExportacion(UsuarioActual.id(jwt), id, filtro);

        StreamingResponseBody cuerpo = salida -> {
            try (respuestaCore) {   // pase lo que pase, cerramos la conexión con el core
                long bytes = respuestaCore.getBody().transferTo(salida);
                log.info("Exportación CSV de la cuenta {} enviada: {} bytes", id, bytes);
            }
        };

        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        String disposicion = respuestaCore.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (disposicion != null) {
            respuesta.header(HttpHeaders.CONTENT_DISPOSITION, disposicion);   // el nombre del fichero
        }
        return respuesta.body(cuerpo);
    }

    @Operation(summary = "Cerrar una cuenta (solo con saldo 0)")
    @PostMapping("/{id}/cerrar")
    public CuentaDto cerrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return core.cerrarCuenta(UsuarioActual.id(jwt), id);
    }

    @Operation(summary = "Cuentas activas en versión ligera (para desplegables)")
    @GetMapping("/ligeras")
    public List<CuentaLigeraDto> ligeras(@AuthenticationPrincipal Jwt jwt) {
        return core.cuentasLigeras(UsuarioActual.id(jwt));
    }

    @Operation(summary = "Ingresos y gastos por mes de los últimos N meses")
    @GetMapping("/{id}/resumen-mensual")
    public List<ResumenMensualDto> resumenMensual(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                  @RequestParam(defaultValue = "12") int meses) {
        return core.resumenMensual(UsuarioActual.id(jwt), id, meses);
    }
}
