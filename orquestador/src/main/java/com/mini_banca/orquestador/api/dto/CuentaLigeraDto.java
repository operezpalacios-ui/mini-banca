package com.mini_banca.orquestador.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaLigeraDto(Long id, String iban, String alias, BigDecimal saldo) {
}
