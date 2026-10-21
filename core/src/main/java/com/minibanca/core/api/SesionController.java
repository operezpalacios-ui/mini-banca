package com.minibanca.core.api;

import com.minibanca.core.api.dto.CrearRefreshTokenRequest;
import com.minibanca.core.api.dto.RefreshTokenRequest;
import com.minibanca.core.api.dto.RefreshTokenResponse;
import com.minibanca.core.api.dto.RotacionResponse;
import com.minibanca.core.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Refresh tokens (RF-1.3). Los guarda el core porque el orquestador no tiene base de datos. */
@RestController
@RequestMapping("/internal/v1/auth/refresh-tokens")
public class SesionController {

    private final RefreshTokenService refreshTokens;

    public SesionController(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefreshTokenResponse crear(@Valid @RequestBody CrearRefreshTokenRequest request) {
        return refreshTokens.crear(request.usuarioId());
    }

    @PostMapping("/rotar")
    public RotacionResponse rotar(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokens.rotar(request.refreshToken());
    }

    @PostMapping("/revocar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokens.revocar(request.refreshToken());
    }
}
