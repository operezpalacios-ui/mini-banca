package com.mini_banca.orquestador.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferenciaRequest(
        @NotNull(message = "es obligatoria")
        Long cuentaOrigenId,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "ES\\d{22}", message = "debe ser un IBAN español: ES + 22 dígitos")
        String ibanDestino,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.01", message = "debe ser mayor que 0")
        @Digits(integer = 17, fraction = 2, message = "máximo 2 decimales")
        BigDecimal importe,

        @Size(max = 140, message = "máximo 140 caracteres")
        String concepto
) {
}
