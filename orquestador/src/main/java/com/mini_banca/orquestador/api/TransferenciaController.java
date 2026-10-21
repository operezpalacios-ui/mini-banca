package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.TransferenciaDto;
import com.mini_banca.orquestador.api.dto.TransferenciaRequest;
import com.mini_banca.orquestador.seguridad.UsuarioActual;
import com.mini_banca.orquestador.servicio.TransferenciaSagaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transferencias")
public class TransferenciaController {

    private final TransferenciaSagaService saga;

    public TransferenciaController(TransferenciaSagaService saga) {
        this.saga = saga;
    }

    /** X-Simular-Fallo-Notificacion: solo para la lección 8 (comprobar que la transferencia NO se deshace). */
    @PostMapping
    public ResponseEntity<TransferenciaDto> transferir(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Simular-Fallo-Notificacion", defaultValue = "false") boolean simularFalloNotificacion,
            @Valid @RequestBody TransferenciaRequest request) {

        ResponseEntity<TransferenciaDto> respuestaCore =
                saga.transferir(UsuarioActual.id(jwt), idempotencyKey, request, simularFalloNotificacion);

        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(respuestaCore.getStatusCode());
        String repetida = respuestaCore.getHeaders().getFirst("Idempotent-Replayed");
        if (repetida != null) {
            respuesta.header("Idempotent-Replayed", repetida);
        }
        return respuesta.body(respuestaCore.getBody());
    }
}
