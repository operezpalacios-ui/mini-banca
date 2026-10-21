package com.mini_banca.orquestador.seguridad;

import org.springframework.security.oauth2.jwt.Jwt;

/** Saca el id del usuario del token ya VALIDADO. Ya no nos fiamos de lo que diga el navegador. */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
