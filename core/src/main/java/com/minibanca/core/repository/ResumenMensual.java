package com.minibanca.core.repository;

import java.math.BigDecimal;

/** Lección 9 (RF-7.6): proyección para la consulta NATIVA. Getter = alias de la columna en el SQL. */
public interface ResumenMensual {
    String getMes();               // "2026-10"
    BigDecimal getIngresos();      // suma de ABONOS
    BigDecimal getGastos();        // suma de CARGOS
    Long getNumMovimientos();
}
