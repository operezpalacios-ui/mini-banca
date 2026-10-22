package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.Pagina;
import com.mini_banca.orquestador.api.dto.TransferenciaDto;
import com.mini_banca.orquestador.api.dto.TransferenciaRequest;
import com.mini_banca.orquestador.cliente.CoreClient;
import com.mini_banca.orquestador.seguridad.UsuarioActual;
import com.mini_banca.orquestador.servicio.TransferenciaSagaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transferencias")
@RestController
@RequestMapping("/api/v1/transferencias")
public class TransferenciaController {

    private final TransferenciaSagaService saga;
    private final CoreClient core;

    public TransferenciaController(TransferenciaSagaService saga, CoreClient core) {
        this.saga = saga;
        this.core = core;
    }

    /** Lección 9: mis transferencias enviadas. ?estado=ANULADA para ver solo las anuladas. */
    @Operation(summary = "Mis transferencias enviadas (las más recientes primero)")
    @GetMapping
    public Pagina<TransferenciaDto> mias(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) String estado,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return core.misTransferencias(UsuarioActual.id(jwt), estado, page, size);
    }

    /** X-Simular-Fallo-Notificacion: solo para la lección 8 (comprobar que la transferencia NO se deshace). */
    @Operation(summary = "Hacer una transferencia (cabecera Idempotency-Key obligatoria)")
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
