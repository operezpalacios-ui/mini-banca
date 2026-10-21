package com.minibanca.core.api;

import com.minibanca.core.api.dto.MovimientoResponse;
import com.minibanca.core.service.MovimientoService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/cuentas/{cuentaId}/movimientos")
public class MovimientoController {

    private final MovimientoService movimientoService;

    public MovimientoController(MovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    @GetMapping
    public PagedModel<MovimientoResponse> historico(
            @RequestHeader("X-User-Id") Long usuarioId,
            @PathVariable Long cuentaId,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(movimientoService.historico(usuarioId, cuentaId, pageable));
    }
}
