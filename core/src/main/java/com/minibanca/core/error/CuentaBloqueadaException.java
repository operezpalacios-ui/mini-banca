package com.minibanca.core.error;

import java.time.Instant;

/** Demasiados intentos fallidos. -> 401 con un mensaje distinto */
public class CuentaBloqueadaException extends NegocioException {
    public CuentaBloqueadaException(Instant hasta) {
        super("Cuenta bloqueada temporalmente por demasiados intentos fallidos. Inténtalo después de " + hasta);
    }
}
