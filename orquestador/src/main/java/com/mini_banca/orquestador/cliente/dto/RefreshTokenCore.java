package com.mini_banca.orquestador.cliente.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshTokenCore(String refreshToken, Instant expiraEn) {
}
