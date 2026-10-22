package com.minibanca.core.repository;

/** Proyección con un RECORD (JPQL "select new ..."): otra forma de pedir solo lo que necesitas. */
public record CuentasDeTransferencia(Long origenId, Long destinoId) {
}
