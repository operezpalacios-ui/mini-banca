package com.minibanca.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de ENTRADA: solo los campos que el cliente puede enviar.
 * Fíjate en lo que NO está: ni id, ni saldo, ni iban, ni estado. Esos los decide el servidor.
 */
public record CrearCuentaRequest(

        @Size(max = 100, message = "máximo 100 caracteres")
        String alias,

        @NotBlank(message = "es obligatoria")
        @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO de 3 letras, por ejemplo EUR")
        String divisa
) {
}
