package com.mini_banca.orquestador.servicio;

import java.math.BigDecimal;

public class LimiteDiarioExcedidoException extends RuntimeException {
    public LimiteDiarioExcedidoException(BigDecimal limite, BigDecimal yaTransferido) {
        super("Superarías tu límite diario de " + limite + " €. Hoy ya has transferido " + yaTransferido + " €.");
    }
}
