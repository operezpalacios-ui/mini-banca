package com.minibanca.core.api.dto;

import jakarta.validation.constraints.NotNull;

public record BloqueoRequest(@NotNull(message = "es obligatorio") Boolean bloqueado) {
}
