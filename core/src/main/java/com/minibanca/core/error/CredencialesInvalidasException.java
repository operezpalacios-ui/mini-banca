package com.minibanca.core.error;

/**
 * Email o contraseña incorrectos. -> 401
 * El mensaje es SIEMPRE el mismo, exista o no el email: así un atacante no puede
 * averiguar qué emails están registrados ("enumeración de usuarios").
 */
public class CredencialesInvalidasException extends NegocioException {
    public CredencialesInvalidasException() {
        super("Email o contraseña incorrectos");
    }
}
