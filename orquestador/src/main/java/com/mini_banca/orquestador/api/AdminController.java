package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.AuditoriaDto;
import com.mini_banca.orquestador.api.dto.BloqueoRequest;
import com.mini_banca.orquestador.api.dto.Pagina;
import com.mini_banca.orquestador.api.dto.UsuarioDto;
import com.mini_banca.orquestador.cliente.CoreClient;
import com.mini_banca.orquestador.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** /api/admin/** -> SOLO rol ADMIN (lo exige SecurityConfig). RF-1.4 y RF-6. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CoreClient core;

    public AdminController(CoreClient core) {
        this.core = core;
    }

    /** RF-6.1 */
    @GetMapping("/usuarios")
    public Pagina<UsuarioDto> listar(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return core.listarUsuarios(UsuarioActual.id(jwt), page, size);
    }

    /** RF-6.2: { "bloqueado": true } o { "bloqueado": false } */
    @PutMapping("/usuarios/{id}/bloqueo")
    public UsuarioDto cambiarBloqueo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody BloqueoRequest request) {
        return core.cambiarBloqueo(UsuarioActual.id(jwt), id, request.bloqueado());
    }

    /** RF-6.3: auditoría filtrable. Fechas en formato ISO, p. ej. 2026-10-21T00:00:00Z */
    @GetMapping("/auditoria")
    public Pagina<AuditoriaDto> auditoria(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(required = false) Long usuarioId,
                                          @RequestParam(required = false) String accion,
                                          @RequestParam(required = false) String desde,
                                          @RequestParam(required = false) String hasta,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return core.buscarAuditoria(UsuarioActual.id(jwt), usuarioId, accion, desde, hasta, page, size);
    }
}
