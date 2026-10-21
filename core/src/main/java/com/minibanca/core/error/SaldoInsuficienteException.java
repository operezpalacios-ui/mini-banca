package com.minibanca.core.error;

/** Caso concreto de regla de negocio, con su propio título en la respuesta. -> 422 */
public class SaldoInsuficienteException extends ReglaNegocioException {
    public SaldoInsuficienteException(String iban) {
        super("La cuenta " + iban + " no tiene saldo suficiente para esta operación");
    }
}
