package com.minibanca.core.api;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.api.dto.TransferenciaResponse;
import com.minibanca.core.service.ModoBloqueo;
import com.minibanca.core.service.TransferenciaService;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
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
     * ?bloqueo=PESIMISTA|OPTIMISTA y la cabecera X-Simular-Fallo son SOLO para la lección 4.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferenciaResponse transferir(
            @RequestHeader("X-User-Id") Long usuarioId,
            @RequestHeader(value = "X-Simular-Fallo", defaultValue = "false") boolean simularFallo,
            @RequestParam(defaultValue = "PESIMISTA") ModoBloqueo bloqueo,
            @Valid @RequestBody CrearTransferenciaRequest request) {
        try {
            return transferenciaService.transferir(usuarioId, request, bloqueo, simularFallo);
        } catch (OptimisticLockingFailureException e) {
            // Con bloqueo optimista, si otra petición modificó la cuenta a la vez,
            // Hibernate lo detecta gracias a @Version y la transacción se deshace entera.
            // (En la lección 5 esto pasará a un manejador global de errores.)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cuenta ha cambiado mientras operabas. Vuelve a intentarlo.");
        }
    }
}
