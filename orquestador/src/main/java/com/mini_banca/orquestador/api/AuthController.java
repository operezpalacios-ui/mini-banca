package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.*;
import com.mini_banca.orquestador.cliente.CoreClient;
import com.mini_banca.orquestador.cliente.dto.RefreshTokenCore;
import com.mini_banca.orquestador.cliente.dto.RotacionCore;
import com.mini_banca.orquestador.seguridad.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CoreClient core;
    private final TokenService tokens;

    public AuthController(CoreClient core, TokenService tokens) {
        this.core = core;
        this.tokens = tokens;
    }

    /** Público. El core guarda el usuario con la contraseña en BCrypt. */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDto registro(@Valid @RequestBody RegistroRequest request) {
        return core.registrarUsuario(request);
    }

    /** Público. Devuelve el JWT (15 min) + un refresh token (7 días, un solo uso). */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        UsuarioDto usuario = core.verificarCredenciales(request);
        RefreshTokenCore refresh = core.crearRefreshToken(usuario.id());
        return new TokenResponse(tokens.emitir(usuario), "Bearer", tokens.segundosDeValidez(), refresh.refreshToken());
    }

    /**
     * Público (RF-1.3). Canjea el refresh token por un JWT nuevo + un refresh token nuevo.
     * El refresh usado queda INVÁLIDO: si alguien lo vuelve a usar, el core cierra todas las sesiones.
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        RotacionCore rotacion = core.rotarRefreshToken(request.refreshToken());
        return new TokenResponse(tokens.emitir(rotacion.usuario()), "Bearer", tokens.segundosDeValidez(),
                rotacion.refreshToken());
    }

    /** Público (RF-1.3). Invalida el refresh token. El JWT actual caducará solo en unos minutos. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        core.revocarRefreshToken(request.refreshToken());
    }

    /** Protegido. "¿Quién soy?": devuelve lo que lleva dentro tu token. */
    @GetMapping("/yo")
    public Map<String, Object> yo(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "id", jwt.getSubject(),
                "nombre", jwt.getClaimAsString("nombre"),
                "rol", jwt.getClaimAsString("rol"),
                "caduca", jwt.getExpiresAt().toString());
    }
}
