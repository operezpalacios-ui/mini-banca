package com.minibanca.core.api.dto;

import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.domain.AuditLog;

import java.time.Instant;

public record AuditoriaResponse(Long id, Instant fecha, Long usuarioId, AccionAuditoria accion,
                                String detalle, String traceId) {
    public static AuditoriaResponse from(AuditLog a) {
        return new AuditoriaResponse(a.getId(), a.getFecha(), a.getUsuarioId(), a.getAccion(),
                a.getDetalle(), a.getTraceId());
    }
}
