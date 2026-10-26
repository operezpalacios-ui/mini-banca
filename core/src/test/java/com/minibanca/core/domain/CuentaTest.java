package com.minibanca.core.domain;

import com.minibanca.core.error.ReglaNegocioException;
import com.minibanca.core.error.SaldoInsuficienteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO: prueba UNA clase aislada, sin Spring, sin base de datos, sin red.
 * Tarda milisegundos. Es el tipo de test que más debes tener.
 *
 * Cada test sigue el patrón "Dado / Cuando / Entonces" (Arrange / Act / Assert):
 *   Dado    una cuenta con 50 €
 *   Cuando  intento sacar 80 €
 *   Entonces falla y el saldo sigue siendo 50 €
 */
class CuentaTest {

    private static Cuenta cuentaCon(String saldo) {
        Usuario ana = new Usuario("ana@test.com", "hash", "Ana", Rol.USER);
        Cuenta cuenta = new Cuenta(ana, "ES0000000000000000000001", "Pruebas", "EUR");
        BigDecimal inicial = new BigDecimal(saldo);
        if (inicial.signum() > 0) {
            cuenta.acreditar(inicial);
        }
        return cuenta;
    }

    @Test
    @DisplayName("Debitar con saldo suficiente resta el importe")
    void debitarConSaldoSuficiente() {
        Cuenta cuenta = cuentaCon("100.00");

        cuenta.debitar(new BigDecimal("30.50"));

        // isEqualByComparingTo: compara el VALOR (69.5 == 69.50). Con BigDecimal nunca uses equals,
        // porque new BigDecimal("69.5").equals(new BigDecimal("69.50")) es FALSE (distinta escala).
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("69.50");
    }

    @Test
    @DisplayName("Debitar más de lo que hay lanza SaldoInsuficiente y NO toca el saldo")
    void debitarSinSaldo() {
        Cuenta cuenta = cuentaCon("50.00");

        assertThatThrownBy(() -> cuenta.debitar(new BigDecimal("80.00")))
                .isInstanceOf(SaldoInsuficienteException.class);

        assertThat(cuenta.getSaldo()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Se puede sacar exactamente todo el saldo (queda a 0)")
    void debitarTodo() {
        Cuenta cuenta = cuentaCon("25.00");

        cuenta.debitar(new BigDecimal("25.00"));

        assertThat(cuenta.getSaldo()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Cerrar una cuenta con saldo 0 la deja CERRADA con fecha de cierre")
    void cerrarConSaldoCero() {
        Cuenta cuenta = cuentaCon("0");

        cuenta.cerrar();

        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.CERRADA);
        assertThat(cuenta.getCerradaEn()).isNotNull();
        assertThat(cuenta.isActiva()).isFalse();
    }

    @Test
    @DisplayName("No se puede cerrar una cuenta que tiene dinero")
    void cerrarConDinero() {
        Cuenta cuenta = cuentaCon("10.00");

        assertThatThrownBy(cuenta::cerrar)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("saldo 0");

        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    @DisplayName("No se puede cerrar dos veces")
    void cerrarDosVeces() {
        Cuenta cuenta = cuentaCon("0");
        cuenta.cerrar();

        assertThatThrownBy(cuenta::cerrar)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya está cerrada");
    }
}
