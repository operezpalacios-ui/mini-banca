package com.minibanca.core.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroUsuarioRequest(
        @NotBlank(message = "es obligatorio") @Email(message = "no es un email válido")
        String email,

        // BCrypt solo usa los primeros 72 bytes: por eso el máximo
        @NotBlank(message = "es obligatoria") @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
        String password,

        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "máximo 100 caracteres")
        String nombre
) {
}
