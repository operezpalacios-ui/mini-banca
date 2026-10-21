package com.minibanca.core.domain;

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
}
