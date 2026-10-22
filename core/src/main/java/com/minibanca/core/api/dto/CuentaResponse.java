package com.minibanca.core.api.dto;

import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.EstadoCuenta;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de SALIDA: lo que el cliente ve. Nunca devolvemos la entidad Cuenta directamente
 * (expondría el usuario con su passwordHash, la columna version, y provocaría errores LAZY).
 */
public record CuentaResponse(
        Long id,
        String iban,
        String alias,
        String divisa,
        BigDecimal saldo,
        EstadoCuenta estado,
        Instant creadoEn,
        Instant cerradaEn          // lección 9: null si está abierta
) {
    public static CuentaResponse from(Cuenta c) {
        return new CuentaResponse(c.getId(), c.getIban(), c.getAlias(), c.getDivisa(),
                c.getSaldo(), c.getEstado(), c.getCreadoEn(), c.getCerradaEn());
    }
}
