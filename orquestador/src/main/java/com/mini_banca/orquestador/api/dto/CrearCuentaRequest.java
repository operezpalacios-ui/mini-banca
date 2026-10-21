package com.mini_banca.orquestador.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El orquestador VALIDA la entrada antes de molestar al core (RF-0 "validación de entrada"). */
public record CrearCuentaRequest(
        @Size(max = 100, message = "máximo 100 caracteres")
        String alias,

        @NotBlank(message = "es obligatoria")
        @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO de 3 letras, por ejemplo EUR")
        String divisa
) {
}
