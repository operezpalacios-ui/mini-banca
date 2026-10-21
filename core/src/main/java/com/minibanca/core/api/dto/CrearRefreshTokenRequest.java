package com.minibanca.core.api.dto;

import jakarta.validation.constraints.NotNull;

public record CrearRefreshTokenRequest(@NotNull Long usuarioId) {
}
