package com.mini_banca.orquestador.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumenMensualDto(String mes, BigDecimal ingresos, BigDecimal gastos, long numMovimientos) {
}
