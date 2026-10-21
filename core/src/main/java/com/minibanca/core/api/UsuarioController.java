package com.minibanca.core.api;

import com.minibanca.core.api.dto.BloqueoRequest;
import com.minibanca.core.api.dto.RegistroUsuarioRequest;
import com.minibanca.core.api.dto.UsuarioResponse;
import com.minibanca.core.api.dto.VerificarCredencialesRequest;
import com.minibanca.core.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints INTERNOS de usuarios (solo los llama el orquestador).
 * El core guarda los usuarios y comprueba contraseñas, pero NO emite JWT:
 * la clave para firmar tokens solo la conoce el orquestador.
 */
@RestController
@RequestMapping("/internal/v1")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistroUsuarioRequest request) {
        return usuarioService.registrar(request);
    }

    @PostMapping("/auth/verificar")
    public UsuarioResponse verificar(@Valid @RequestBody VerificarCredencialesRequest request) {
        return usuarioService.verificar(request);
    }

    @GetMapping("/usuarios")
    public PagedModel<UsuarioResponse> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(usuarioService.listar(pageable));
    }

    /** RF-6.2: bloquear / desbloquear. X-User-Id es el ADMIN que lo hace (queda en la auditoría). */
    @PutMapping("/usuarios/{id}/bloqueo")
    public UsuarioResponse cambiarBloqueo(@RequestHeader("X-User-Id") Long adminId,
                                          @PathVariable Long id,
                                          @Valid @RequestBody BloqueoRequest request) {
        return usuarioService.cambiarBloqueo(adminId, id, request.bloqueado());
    }
}
