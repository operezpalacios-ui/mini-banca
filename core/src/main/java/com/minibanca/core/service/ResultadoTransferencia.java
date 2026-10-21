package com.minibanca.core.service;

import com.minibanca.core.api.dto.TransferenciaResponse;

/** repetida = true si la Idempotency-Key ya se había usado y devolvemos la transferencia original. */
public record ResultadoTransferencia(TransferenciaResponse transferencia, boolean repetida) {
}
