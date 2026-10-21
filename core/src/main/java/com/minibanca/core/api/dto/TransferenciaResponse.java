package com.minibanca.core.api.dto;

import com.minibanca.core.domain.Transferencia;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferenciaResponse(
        Long id,
        Long cuentaOrigenId,
        String ibanOrigen,
        String ibanDestino,
        BigDecimal importe,
        String concepto,
        Instant fecha,
        BigDecimal saldoOrigenTrasOperacion
) {
    public static TransferenciaResponse from(Transferencia t) {
        return new TransferenciaResponse(
                t.getId(),
                t.getCuentaOrigen().getId(),
                t.getCuentaOrigen().getIban(),
                t.getCuentaDestino().getIban(),
                t.getImporte(),
                t.getConcepto(),
                t.getFecha(),
                t.getCuentaOrigen().getSaldo());
    }
}
