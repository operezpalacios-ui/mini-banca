package com.mini_banca.orquestador.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO PROPIO del orquestador. Se parece al CuentaResponse del core, pero NO es la misma clase:
 * los servicios no comparten código, solo el contrato JSON.
 * ignoreUnknown = true -> si el core añade campos nuevos, no nos rompe ("lector tolerante").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaDto(Long id, String iban, String alias, String divisa,
                        BigDecimal saldo, String estado, Instant creadoEn) {
}
