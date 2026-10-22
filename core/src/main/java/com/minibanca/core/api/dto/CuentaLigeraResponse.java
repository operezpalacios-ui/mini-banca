package com.minibanca.core.api.dto;

import com.minibanca.core.repository.CuentaLigera;

import java.math.BigDecimal;

/** Copiamos la proyección a un record: al JSON sale un objeto normal, no un "proxy" de Spring. */
public record CuentaLigeraResponse(Long id, String iban, String alias, BigDecimal saldo) {
    public static CuentaLigeraResponse from(CuentaLigera c) {
        return new CuentaLigeraResponse(c.getId(), c.getIban(), c.getAlias(), c.getSaldo());
    }
}
