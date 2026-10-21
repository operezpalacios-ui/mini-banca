package com.minibanca.core.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank(message = "es obligatorio") String refreshToken) {
}
