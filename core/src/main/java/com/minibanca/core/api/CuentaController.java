package com.minibanca.core.api;

import com.minibanca.core.api.dto.CrearCuentaRequest;
import com.minibanca.core.api.dto.CuentaConMovimientosResponse;
import com.minibanca.core.api.dto.CuentaLigeraResponse;
import com.minibanca.core.api.dto.CuentaResponse;
import com.minibanca.core.api.dto.ResumenMensualResponse;
import com.minibanca.core.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Capa de CONTROLLER: solo traduce HTTP <-> Java. Nada de reglas de negocio aquí.
 *
 * Es una API INTERNA (/internal/v1): en el futuro solo la llamará el orquestador.
 * La cabecera X-User-Id simula la identidad que el orquestador propagará tras validar el JWT (RF-0.3).
 */
@RestController
@RequestMapping("/internal/v1/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaResponse crear(@RequestHeader("X-User-Id") Long usuarioId,
                                @Valid @RequestBody CrearCuentaRequest request) {
        return cuentaService.crear(usuarioId, request);
    }

    @GetMapping
    public PagedModel<CuentaResponse> listar(
            @RequestHeader("X-User-Id") Long usuarioId,
            @PageableDefault(size = 10, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(cuentaService.listar(usuarioId, pageable));
    }

    // Lección 3: GET /internal/v1/cuentas/resumen?optimizado=false  (o true)
    @GetMapping("/resumen")
    public List<CuentaConMovimientosResponse> resumen(@RequestHeader("X-User-Id") Long usuarioId,
                                                      @RequestParam(defaultValue = "false") boolean optimizado) {
        return cuentaService.resumen(usuarioId, optimizado);
    }

    @GetMapping("/{id}")
    public CuentaResponse detalle(@RequestHeader("X-User-Id") Long usuarioId,
                                  @PathVariable Long id) {
        return cuentaService.detalle(usuarioId, id);
    }

    // ---------- Lección 9 ----------

    /**
     * RF-2.3. POST a una "acción" (/cerrar) en vez de DELETE: la cuenta NO desaparece,
     * cambia de estado. DELETE daría a entender que la borramos.
     */
    @PostMapping("/{id}/cerrar")
    public CuentaResponse cerrar(@RequestHeader("X-User-Id") Long usuarioId, @PathVariable Long id) {
        return cuentaService.cerrar(usuarioId, id);
    }

    /** RF-7.6 (proyección). La ruta fija /ligeras tiene prioridad sobre /{id}. */
    @GetMapping("/ligeras")
    public List<CuentaLigeraResponse> ligeras(@RequestHeader("X-User-Id") Long usuarioId) {
        return cuentaService.ligeras(usuarioId);
    }

    /** RF-7.6 (consulta nativa): GET /internal/v1/cuentas/1/resumen-mensual?meses=6 */
    @GetMapping("/{id}/resumen-mensual")
    public List<ResumenMensualResponse> resumenMensual(@RequestHeader("X-User-Id") Long usuarioId,
                                                       @PathVariable Long id,
                                                       @RequestParam(defaultValue = "12") int meses) {
        return cuentaService.resumenMensual(usuarioId, id, meses);
    }
}
