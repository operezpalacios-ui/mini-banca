package com.minibanca.core.error;

/** Algo que no existe, o que existe pero no es tuyo (no lo revelamos). -> 404 */
public class RecursoNoEncontradoException extends NegocioException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
