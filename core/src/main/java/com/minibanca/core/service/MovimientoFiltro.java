package com.minibanca.core.service;

import com.minibanca.core.domain.TipoMovimiento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lección 9 (RF-4.2): los filtros del histórico. TODOS opcionales (null = "no filtrar por esto").
 * Las fechas son DÍAS (2026-10-01), no instantes: "hasta" incluye el día entero.
 */
public record MovimientoFiltro(
        LocalDate desde,
        LocalDate hasta,
        TipoMovimiento tipo,
        BigDecimal importeMin,
        BigDecimal importeMax,
        String texto
) {
}
