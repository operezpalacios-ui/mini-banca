package com.minibanca.core.api;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.api.dto.TransferenciaResponse;
import com.minibanca.core.service.ModoBloqueo;
import com.minibanca.core.service.ResultadoTransferencia;
import com.minibanca.core.service.TransferenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/transferencias")
public class TransferenciaController {

    private final TransferenciaService transferenciaService;

    public TransferenciaController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    /**
     * Lección 5:
     *  - La cabecera Idempotency-Key es OBLIGATORIA (si falta -> 400).
     *  - Ya no hay try/catch: los errores los traduce el GlobalExceptionHandler.
     *  - Si la clave ya se usó, devolvemos la transferencia ORIGINAL con la cabecera
     *    Idempotent-Replayed: true, y no se mueve ni un céntimo.
     */
    @PostMapping
    public ResponseEntity<TransferenciaResponse> transferir(
            @RequestHeader("X-User-Id") Long usuarioId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Simular-Fallo", defaultValue = "false") boolean simularFallo,
            @RequestParam(defaultValue = "PESIMISTA") ModoBloqueo bloqueo,
            @Valid @RequestBody CrearTransferenciaRequest request) {

        // Comprobación de formato de la cabecera (es un asunto HTTP, por eso vive en el controller)
        if (idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabecera Idempotency-Key debe tener entre 8 y 100 caracteres");
        }

        ResultadoTransferencia resultado =
                transferenciaService.transferir(usuarioId, idempotencyKey, request, bloqueo, simularFallo);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(resultado.repetida()))
                .body(resultado.transferencia());
    }
}
