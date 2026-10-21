package com.minibanca.core.domain;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;   // nunca la contraseña en claro: aquí irá el hash BCrypt

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)   // guarda "USER"/"ADMIN", no 0/1
    @Column(nullable = false)
    private Rol rol;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    // Lección 7: protección contra fuerza bruta
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    // Lección 8 (RF-6.2): un ADMIN puede desactivar la cuenta indefinidamente
    @Column(nullable = false)
    private boolean activo = true;

    protected Usuario() {
        // JPA necesita un constructor vacío
    }

    public Usuario(String email, String passwordHash, String nombre, Rol rol) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.rol = rol;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public Instant getCreadoEn() { return creadoEn; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public Instant getBloqueadoHasta() { return bloqueadoHasta; }
    public boolean isActivo() { return activo; }

    public void desactivar() { this.activo = false; }

    public void activar() { this.activo = true; }

    // --- Lección 7: reglas de login, dentro de la entidad ---

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    /** Suma un fallo. Al llegar al máximo, bloquea la cuenta durante 'duracion' y reinicia el contador. */
    public void registrarIntentoFallido(Instant ahora, int maximo, Duration duracion) {
        intentosFallidos++;
        if (intentosFallidos >= maximo) {
            bloqueadoHasta = ahora.plus(duracion);
            intentosFallidos = 0;
        }
    }

    public void registrarAccesoCorrecto() {
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }
}
