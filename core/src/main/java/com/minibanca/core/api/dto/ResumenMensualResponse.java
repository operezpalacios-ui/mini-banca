package com.minibanca.core.api.dto;

import com.minibanca.core.repository.ResumenMensual;

import java.math.BigDecimal;

public record ResumenMensualResponse(String mes, BigDecimal ingresos, BigDecimal gastos, long numMovimientos) {
    public static ResumenMensualResponse from(ResumenMensual r) {
        return new ResumenMensualResponse(r.getMes(), r.getIngresos(), r.getGastos(), r.getNumMovimientos());
    }
}
