package com.minibanca.core.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "movimiento")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lado "muchos": cada movimiento pertenece a UNA cuenta. Este lado es el PROPIETARIO
    // de la relación (tiene la columna cuenta_id).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal importe;

    private String concepto;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    protected Movimiento() {
    }

    public Movimiento(Cuenta cuenta, TipoMovimiento tipo, BigDecimal importe, String concepto) {
        this.cuenta = cuenta;
        this.tipo = tipo;
        this.importe = importe;
        this.concepto = concepto;
    }

    public Long getId() { return id; }
    public Cuenta getCuenta() { return cuenta; }
    public TipoMovimiento getTipo() { return tipo; }
    public BigDecimal getImporte() { return importe; }
    public String getConcepto() { return concepto; }
    public Instant getFecha() { return fecha; }
}
