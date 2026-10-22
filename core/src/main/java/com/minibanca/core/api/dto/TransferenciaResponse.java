package com.minibanca.core.api.dto;

import com.minibanca.core.domain.EstadoTransferencia;
import com.minibanca.core.domain.Transferencia;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lección 5: ya no incluye el saldo tras la operación. Con idempotencia, repetir la petición
 * debe devolver EXACTAMENTE el resultado original, y el saldo "de ahora" podría ser otro.
 */
public record TransferenciaResponse(
        Long id,
        Long cuentaOrigenId,
        String ibanOrigen,
        String ibanDestino,
        BigDecimal importe,
        String concepto,
        Instant fecha,
        // Lección 9 (RF-3.6)
        EstadoTransferencia estado,
        Instant anuladaEn,
        String motivoAnulacion
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
                t.getEstado(),
                t.getAnuladaEn(),
                t.getMotivoAnulacion());
    }
}
