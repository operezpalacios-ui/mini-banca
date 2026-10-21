package com.minibanca.core.error;

/**
 * Raíz de NUESTRAS excepciones (RF-8.4). Fíjate en que no saben nada de HTTP:
 * el dominio dice QUÉ ha pasado; el GlobalExceptionHandler decide CÓMO se responde.
 */
public abstract class NegocioException extends RuntimeException {
    protected NegocioException(String mensaje) {
        super(mensaje);
    }
}
