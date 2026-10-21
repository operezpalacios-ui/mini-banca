package com.minibanca.core.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Muchas cuentas -> un usuario. LAZY: el usuario no se carga hasta que se usa.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, unique = true)
    private String iban;

    private String alias;

    @Column(nullable = false)
    private String divisa;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;   // dinero SIEMPRE en BigDecimal, nunca double

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCuenta estado = EstadoCuenta.ACTIVA;

    @Version   // bloqueo optimista (lo usaremos en la lección de transferencias)
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    protected Cuenta() {
    }

    public Cuenta(Usuario usuario, String iban, String alias, String divisa) {
        this.usuario = usuario;
        this.iban = iban;
        this.alias = alias;
        this.divisa = divisa;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getIban() { return iban; }
    public String getAlias() { return alias; }
    public String getDivisa() { return divisa; }
    public BigDecimal getSaldo() { return saldo; }
    public EstadoCuenta getEstado() { return estado; }
    public Long getVersion() { return version; }
    public Instant getCreadoEn() { return creadoEn; }
}
