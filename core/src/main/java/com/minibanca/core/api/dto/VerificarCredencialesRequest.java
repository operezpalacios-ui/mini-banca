package com.minibanca.core.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerificarCredencialesRequest(
        @NotBlank(message = "es obligatorio") String email,
        @NotBlank(message = "es obligatoria") String password
) {
}
