package com.minibanca.core.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST PARAMETRIZADO: el MISMO test con muchos datos distintos. Cada fila de @CsvSource es un caso:
 *   'lo que entra' , 'lo que debe salir'
 * (Separamos con | y usamos ` como "comillas" de CSV, porque ' y " forman parte de los datos.)
 */
class CsvCeldaTest {

    @ParameterizedTest(name = "[{index}] {0}  ->  {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "Transferencia a ES12          | Transferencia a ES12",          // texto normal: igual
            "Cena; copas                   | \"Cena; copas\"",               // lleva ; -> entre comillas
            "Dijo \"hola\"                 | \"Dijo \"\"hola\"\"\"",         // comillas -> se duplican
            "=HYPERLINK(\"http://malo\")   | \"'=HYPERLINK(\"\"http://malo\"\")\"",  // fórmula -> apóstrofo delante
            "+34 600                       | '+34 600",                      // también + - @
            "@SUM(A1)                      | '@SUM(A1)",
    })
    void celdaSegura(String entrada, String esperado) {
        assertThat(MovimientoController.celda(entrada)).isEqualTo(esperado);
    }

    @org.junit.jupiter.api.Test
    void nullEsCeldaVacia() {
        assertThat(MovimientoController.celda(null)).isEmpty();
    }
}
