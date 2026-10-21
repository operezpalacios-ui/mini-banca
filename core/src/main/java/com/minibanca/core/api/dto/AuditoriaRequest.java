package com.minibanca.core.api.dto;

import com.minibanca.core.domain.AccionAuditoria;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditoriaRequest(
        Long usuarioId,
        @NotNull(message = "es obligatoria") AccionAuditoria accion,
        @Size(max = 500) String detalle
) {
}
