package com.mini_banca.orquestador.cliente.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TotalCore(BigDecimal total) {
}
