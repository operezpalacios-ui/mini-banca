package com.minibanca.core.error;

/** Un administrador ha desactivado la cuenta. -> 401 */
public class UsuarioDesactivadoException extends NegocioException {
    public UsuarioDesactivadoException() {
        super("Esta cuenta ha sido desactivada. Contacta con el banco.");
    }
}
