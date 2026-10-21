package com.minibanca.core.error;

/** Choca con algo que ya existe (p. ej. una Idempotency-Key reutilizada con otros datos). -> 409 */
public class ConflictoException extends NegocioException {
    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
