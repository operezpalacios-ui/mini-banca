package com.minibanca.core.api.dto;

import java.time.Instant;

/** Resultado de canjear un refresh token: de quién era y el refresh token NUEVO. */
public record RotacionResponse(UsuarioResponse usuario, String refreshToken, Instant expiraEn) {
}
