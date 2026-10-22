package com.minibanca.core.domain;

import com.minibanca.core.error.ConflictoException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transferencia")
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_origen_id", nullable = false)
    private Cuenta cuentaOrigen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_destino_id", nullable = false)
    private Cuenta cuentaDestino;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal importe;

    private String concepto;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    // Lección 5: la clave con la que el cliente pidió esta transferencia (única en BBDD)
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    // Lección 9 (RF-3.6): anulación por un administrador
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTransferencia estado = EstadoTransferencia.COMPLETADA;

    @Column(name = "anulada_en")
    private Instant anuladaEn;

    @Column(name = "anulada_por")
    private Long anuladaPor;          // id del admin (solo el id: no necesitamos cargar el Usuario)

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    protected Transferencia() {
    }

    public Transferencia(Cuenta cuentaOrigen, Cuenta cuentaDestino, BigDecimal importe, String concepto,
                         String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        this.cuentaOrigen = cuentaOrigen;
        this.cuentaDestino = cuentaDestino;
        this.importe = importe;
        this.concepto = concepto;
    }

    public Long getId() { return id; }
    public Cuenta getCuentaOrigen() { return cuentaOrigen; }
    public Cuenta getCuentaDestino() { return cuentaDestino; }
    public BigDecimal getImporte() { return importe; }
    public String getConcepto() { return concepto; }
    public Instant getFecha() { return fecha; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public EstadoTransferencia getEstado() { return estado; }
    public Instant getAnuladaEn() { return anuladaEn; }
    public Long getAnuladaPor() { return anuladaPor; }
    public String getMotivoAnulacion() { return motivoAnulacion; }

    /** Solo cambia el ESTADO. Devolver el dinero lo hace el servicio (necesita las dos cuentas bloqueadas). */
    public void marcarAnulada(Long adminId, String motivo) {
        if (estado == EstadoTransferencia.ANULADA) {
            // 409: no es un dato incorrecto, es que choca con el estado actual
            throw new ConflictoException("La transferencia " + id + " ya estaba anulada");
        }
        this.estado = EstadoTransferencia.ANULADA;
        this.anuladaEn = Instant.now();
        this.anuladaPor = adminId;
        this.motivoAnulacion = motivo;
    }
}
