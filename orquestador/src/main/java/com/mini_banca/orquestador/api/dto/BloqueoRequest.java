package com.mini_banca.orquestador.api.dto;

import jakarta.validation.constraints.NotNull;

public record BloqueoRequest(@NotNull(message = "es obligatorio") Boolean bloqueado) {
}
