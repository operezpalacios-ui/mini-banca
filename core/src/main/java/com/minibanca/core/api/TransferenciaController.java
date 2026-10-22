package com.minibanca.core.api;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.api.dto.TotalResponse;
import com.minibanca.core.api.dto.AnulacionRequest;
import com.minibanca.core.api.dto.TransferenciaResponse;
import com.minibanca.core.domain.EstadoTransferencia;
import com.minibanca.core.service.ModoBloqueo;
import com.minibanca.core.service.ResultadoTransferencia;
import com.minibanca.core.service.TransferenciaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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

    /** Lección 8: lo usa el orquestador para comprobar el límite diario (RF-0.5, paso 1). */
    @GetMapping("/total-hoy")
    public TotalResponse totalHoy(@RequestHeader("X-User-Id") Long usuarioId) {
        return new TotalResponse(transferenciaService.totalTransferidoHoy(usuarioId));
    }

    // ---------- Lección 9 ----------

    /** Mis transferencias enviadas (las más recientes primero). */
    @GetMapping
    public PagedModel<TransferenciaResponse> mias(
            @RequestHeader("X-User-Id") Long usuarioId,
            @RequestParam(required = false) EstadoTransferencia estado,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(transferenciaService.buscar(usuarioId, estado, pageable));
    }

    /**
     * SOLO ADMIN: todas las transferencias, con filtro opcional por usuario.
     * El core no sabe de roles: confía en que el orquestador solo llama aquí desde /api/admin/**
     * (y nadie más puede llamar al core sin la X-Internal-Key).
     */
    @GetMapping("/admin")
    public PagedModel<TransferenciaResponse> todas(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) EstadoTransferencia estado,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(transferenciaService.buscar(usuarioId, estado, pageable));
    }

    /** SOLO ADMIN (RF-3.6). X-User-Id = el admin que anula (queda en la auditoría). */
    @PostMapping("/{id}/anulacion")
    public TransferenciaResponse anular(@RequestHeader("X-User-Id") Long adminId,
                                        @PathVariable Long id,
                                        @Valid @RequestBody AnulacionRequest request) {
        return transferenciaService.anular(adminId, id, request.motivo());
    }
}
