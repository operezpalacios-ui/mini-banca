package com.minibanca.core.api;

import com.minibanca.core.api.dto.AuditoriaRequest;
import com.minibanca.core.api.dto.AuditoriaResponse;
import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.service.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/internal/v1/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    /** El orquestador registra aquí lo que ocurre en su lado de la saga (transferencias, notificaciones). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void registrar(@Valid @RequestBody AuditoriaRequest request) {
        auditoria.registrar(request.usuarioId(), request.accion(), request.detalle());
    }

    /** RF-6.3: consulta paginada y filtrable por usuario, acción y fechas. */
    @GetMapping
    public PagedModel<AuditoriaResponse> buscar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) AccionAuditoria accion,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(auditoria.buscar(usuarioId, accion, desde, hasta, pageable));
    }
}
