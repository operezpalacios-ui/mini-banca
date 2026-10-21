package com.mini_banca.orquestador.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditoriaDto(Long id, Instant fecha, Long usuarioId, String accion, String detalle, String traceId) {
}
