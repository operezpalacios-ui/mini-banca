package com.minibanca.core.api.dto;

import com.minibanca.core.domain.Cuenta;

import java.math.BigDecimal;
import java.util.List;

public record CuentaConMovimientosResponse(
        Long id,
        String iban,
        String alias,
        BigDecimal saldo,
        List<MovimientoResponse> movimientos
) {
    public static CuentaConMovimientosResponse from(Cuenta c) {
        return new CuentaConMovimientosResponse(
                c.getId(), c.getIban(), c.getAlias(), c.getSaldo(),
                // ⚠️ AQUÍ está el N+1: si los movimientos no se cargaron antes,
                // esta línea lanza UNA consulta nueva por cada cuenta.
                c.getMovimientos().stream().map(MovimientoResponse::from).toList()
        );
    }
}
