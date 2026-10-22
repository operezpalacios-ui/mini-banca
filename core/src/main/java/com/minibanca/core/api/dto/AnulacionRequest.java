package com.minibanca.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anular sin explicar por qué no tiene sentido en un banco: el motivo es obligatorio. */
public record AnulacionRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 200, message = "El motivo debe tener entre 5 y 200 caracteres")
        String motivo
) {
}
