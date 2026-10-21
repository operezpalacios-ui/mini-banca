package com.mini_banca.orquestador.api.dto;

/**
 * Lo que devuelve el login (y el refresh):
 *  - accessToken: el JWT para cada petición (15 min)
 *  - refreshToken: para conseguir un JWT nuevo sin volver a escribir la contraseña (7 días, un solo uso)
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
}
