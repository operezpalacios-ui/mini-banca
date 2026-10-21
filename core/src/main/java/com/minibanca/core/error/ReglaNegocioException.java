package com.minibanca.core.error;

/** La petición se entiende, pero va contra las normas del banco. -> 422 */
public class ReglaNegocioException extends NegocioException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
