package com.minibanca.core.api;

import com.minibanca.core.api.dto.CrearCuentaRequest;
import com.minibanca.core.api.dto.CuentaConMovimientosResponse;
import com.minibanca.core.api.dto.CuentaResponse;
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
}
