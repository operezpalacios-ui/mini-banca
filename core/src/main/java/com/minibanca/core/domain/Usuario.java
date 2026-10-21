package com.minibanca.core.domain;

import jakarta.persistence.*;

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
}
