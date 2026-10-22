package com.mini_banca.orquestador.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferenciaDto(Long id, Long cuentaOrigenId, String ibanOrigen, String ibanDestino,
                               BigDecimal importe, String concepto, Instant fecha,
                               // Lección 9 (RF-3.6)
                               String estado, Instant anuladaEn, String motivoAnulacion) {
}
