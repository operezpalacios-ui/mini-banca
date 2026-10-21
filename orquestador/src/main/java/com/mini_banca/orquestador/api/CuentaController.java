package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.*;
import com.mini_banca.orquestador.cliente.CoreClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * API PÚBLICA (/api/v1): es lo que llamará el navegador.
 * El orquestador valida, añade contexto (usuario, traceId) y delega en el core.
 *
 * ⚠️ X-User-Id es PROVISIONAL: en la lección 7 el usuario saldrá del token JWT
 * y el navegador ya no podrá decir "soy el usuario 1".
 */
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final CoreClient core;

    public CuentaController(CoreClient core) {
        this.core = core;
    }

    @GetMapping
    public Pagina<CuentaDto> listar(@RequestHeader("X-User-Id") Long usuarioId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String sort) {
        return core.listarCuentas(usuarioId, page, size, sort);
    }

    @GetMapping("/{id}")
    public CuentaDto detalle(@RequestHeader("X-User-Id") Long usuarioId, @PathVariable Long id) {
        return core.detalleCuenta(usuarioId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaDto crear(@RequestHeader("X-User-Id") Long usuarioId,
                           @Valid @RequestBody CrearCuentaRequest request) {
        return core.crearCuenta(usuarioId, request);
    }

    @GetMapping("/{id}/movimientos")
    public Pagina<MovimientoDto> movimientos(@RequestHeader("X-User-Id") Long usuarioId,
                                             @PathVariable Long id,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return core.movimientos(usuarioId, id, page, size);
    }
}
