package com.minibanca.core.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private boolean revocado = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    protected RefreshToken() {
    }

    public RefreshToken(Usuario usuario, String tokenHash, Instant expiraEn) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
    }

    public boolean estaVigente(Instant ahora) {
        return !revocado && ahora.isBefore(expiraEn);
    }

    public void revocar() {
        this.revocado = true;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public boolean isRevocado() { return revocado; }
    public Instant getExpiraEn() { return expiraEn; }
}
