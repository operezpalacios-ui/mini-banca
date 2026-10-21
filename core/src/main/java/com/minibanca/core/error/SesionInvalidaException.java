package com.minibanca.core.error;

/** Refresh token inexistente, caducado, revocado o reutilizado. -> 401 */
public class SesionInvalidaException extends NegocioException {
    public SesionInvalidaException() {
        super("La sesión no es válida o ha caducado. Vuelve a iniciar sesión.");
    }
}
