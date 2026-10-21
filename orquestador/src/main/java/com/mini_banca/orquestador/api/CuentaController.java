package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.*;
import com.mini_banca.orquestador.cliente.CoreClient;
import com.mini_banca.orquestador.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * API PÚBLICA (/api/v1): es lo que llamará el navegador.
 * El orquestador valida, añade contexto (usuario, traceId) y delega en el core.
 *
 * Lección 7: el usuario ya NO viene de una cabecera que el navegador pueda inventarse,
 * sino del token JWT, que Spring Security ha validado antes de llegar aquí.
 */
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

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

    @GetMapping("/{id}/movimientos")
    public Pagina<MovimientoDto> movimientos(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable Long id,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return core.movimientos(UsuarioActual.id(jwt), id, page, size);
    }
}
