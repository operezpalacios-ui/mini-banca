package com.minibanca.core.api.dto;

import com.minibanca.core.domain.Rol;
import com.minibanca.core.domain.Usuario;

/** Fíjate en lo que NO va aquí: el passwordHash nunca sale del core. */
public record UsuarioResponse(Long id, String email, String nombre, Rol rol, boolean activo) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getEmail(), u.getNombre(), u.getRol(), u.isActivo());
    }
}
