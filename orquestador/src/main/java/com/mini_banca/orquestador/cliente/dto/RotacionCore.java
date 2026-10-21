package com.mini_banca.orquestador.cliente.dto;

import com.mini_banca.orquestador.api.dto.UsuarioDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RotacionCore(UsuarioDto usuario, String refreshToken, Instant expiraEn) {
}
