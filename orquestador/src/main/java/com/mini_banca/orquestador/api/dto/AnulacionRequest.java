package com.mini_banca.orquestador.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validamos también aquí: si viene mal, ni siquiera molestamos al core. */
public record AnulacionRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 200, message = "El motivo debe tener entre 5 y 200 caracteres")
        String motivo
) {
}
