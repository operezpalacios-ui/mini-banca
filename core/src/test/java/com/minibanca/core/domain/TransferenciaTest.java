package com.minibanca.core.domain;

import com.minibanca.core.error.ConflictoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferenciaTest {

    private static Transferencia nueva() {
        Usuario ana = new Usuario("ana@test.com", "hash", "Ana", Rol.USER);
        Cuenta origen = new Cuenta(ana, "ES0000000000000000000001", null, "EUR");
        Cuenta destino = new Cuenta(ana, "ES0000000000000000000002", null, "EUR");
        return new Transferencia(origen, destino, new BigDecimal("10.00"), "Prueba", "clave-12345678");
    }

    @Test
    void unaTransferenciaNuevaEstaCompletada() {
        assertThat(nueva().getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);
    }

    @Test
    void anularGuardaQuienCuandoYPorQue() {
        Transferencia t = nueva();

        t.marcarAnulada(99L, "Cargo duplicado");

        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.ANULADA);
        assertThat(t.getAnuladaPor()).isEqualTo(99L);
        assertThat(t.getMotivoAnulacion()).isEqualTo("Cargo duplicado");
        assertThat(t.getAnuladaEn()).isNotNull();
    }

    @Test
    void noSePuedeAnularDosVeces() {
        Transferencia t = nueva();
        t.marcarAnulada(99L, "Primera vez");

        assertThatThrownBy(() -> t.marcarAnulada(99L, "Segunda vez"))
                .isInstanceOf(ConflictoException.class);

        assertThat(t.getMotivoAnulacion()).isEqualTo("Primera vez");   // no se ha pisado
    }
}
