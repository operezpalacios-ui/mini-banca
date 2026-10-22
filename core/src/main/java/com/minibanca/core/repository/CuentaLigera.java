package com.minibanca.core.repository;

import java.math.BigDecimal;

/**
 * Lección 9 (RF-7.6): PROYECCIÓN "cerrada". Spring Data implementa esta interfaz por nosotros
 * y solo pide a la BBDD estas columnas. Los nombres de los getters = nombres de los campos de Cuenta.
 */
public interface CuentaLigera {
    Long getId();
    String getIban();
    String getAlias();
    BigDecimal getSaldo();
}
