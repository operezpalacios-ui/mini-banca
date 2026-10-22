package com.minibanca.core.domain;

import com.minibanca.core.error.ReglaNegocioException;
import com.minibanca.core.error.SaldoInsuficienteException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    // Lección 9 (RF-2.3): cuándo se cerró. null = sigue abierta.
    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    // Lado "uno": una cuenta tiene muchos movimientos.
    // mappedBy = "cuenta" -> "el dueño de la relación es el campo 'cuenta' de Movimiento".
    // @OneToMany es LAZY por defecto: los movimientos NO se cargan hasta que se usan.
    @OneToMany(mappedBy = "cuenta")
    @OrderBy("fecha DESC")
    private List<Movimiento> movimientos = new ArrayList<>();

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
    public Instant getCerradaEn() { return cerradaEn; }
    public List<Movimiento> getMovimientos() { return movimientos; }

    // --- Lección 4: comportamiento de dominio ---
    // Las reglas que protegen el saldo viven AQUÍ, dentro de la entidad: nadie puede
    // dejar una cuenta en negativo "por despiste" desde otra clase.

    public boolean isActiva() {
        return estado == EstadoCuenta.ACTIVA;
    }

    public void debitar(BigDecimal importe) {
        if (saldo.compareTo(importe) < 0) {
            throw new SaldoInsuficienteException(iban);   // excepción de DOMINIO (lección 5)
        }
        this.saldo = this.saldo.subtract(importe);
    }

    public void acreditar(BigDecimal importe) {
        this.saldo = this.saldo.add(importe);
    }

    /**
     * Lección 9 (RF-2.3): cerrar la cuenta. Las reglas viven en la ENTIDAD, como debitar():
     *  - no se puede cerrar dos veces;
     *  - solo con saldo 0 (si no, ¿qué hacemos con el dinero? el cliente debe sacarlo antes).
     * No se borra la fila: los movimientos y transferencias antiguas siguen apuntando a ella.
     */
    public void cerrar() {
        if (!isActiva()) {
            throw new ReglaNegocioException("La cuenta ya está cerrada");
        }
        if (saldo.signum() != 0) {
            throw new ReglaNegocioException(
                    "Solo se puede cerrar una cuenta con saldo 0. Saldo actual: " + saldo + " " + divisa);
        }
        this.estado = EstadoCuenta.CERRADA;
        this.cerradaEn = Instant.now();
    }
}
