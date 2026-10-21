package com.minibanca.core.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    // Sin relación @ManyToOne a propósito: un registro de auditoría debe sobrevivir
    // aunque algún día cambie o desaparezca el usuario. Es un "acta", no una relación viva.
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccionAuditoria accion;

    private String detalle;

    @Column(name = "trace_id")
    private String traceId;

    protected AuditLog() {
    }

    public AuditLog(Long usuarioId, AccionAuditoria accion, String detalle, String traceId) {
        this.usuarioId = usuarioId;
        this.accion = accion;
        this.detalle = detalle;
        this.traceId = traceId;
    }

    public Long getId() { return id; }
    public Instant getFecha() { return fecha; }
    public Long getUsuarioId() { return usuarioId; }
    public AccionAuditoria getAccion() { return accion; }
    public String getDetalle() { return detalle; }
    public String getTraceId() { return traceId; }
}
