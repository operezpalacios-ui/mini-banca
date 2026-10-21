package com.minibanca.core.api.dto;

import com.minibanca.core.domain.Movimiento;
import com.minibanca.core.domain.TipoMovimiento;

import java.math.BigDecimal;
import java.time.Instant;

public record MovimientoResponse(
        Long id,
        TipoMovimiento tipo,
        BigDecimal importe,
        String concepto,
        Instant fecha
) {
    public static MovimientoResponse from(Movimiento m) {
        return new MovimientoResponse(m.getId(), m.getTipo(), m.getImporte(), m.getConcepto(), m.getFecha());
    }
}
