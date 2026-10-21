package com.mini_banca.orquestador.api;

import com.mini_banca.orquestador.api.dto.TransferenciaDto;
import com.mini_banca.orquestador.api.dto.TransferenciaRequest;
import com.mini_banca.orquestador.cliente.CoreClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transferencias")
public class TransferenciaController {

    private final CoreClient core;

    public TransferenciaController(CoreClient core) {
        this.core = core;
    }

    @PostMapping
    public ResponseEntity<TransferenciaDto> transferir(@RequestHeader("X-User-Id") Long usuarioId,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       @Valid @RequestBody TransferenciaRequest request) {
        ResponseEntity<TransferenciaDto> respuestaCore = core.transferir(usuarioId, idempotencyKey, request);

        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(respuestaCore.getStatusCode());
        String repetida = respuestaCore.getHeaders().getFirst("Idempotent-Replayed");
        if (repetida != null) {
            respuesta.header("Idempotent-Replayed", repetida);
        }
        return respuesta.body(respuestaCore.getBody());
    }
}
