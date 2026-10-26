package com.minibanca.core;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.Rol;
import com.minibanca.core.domain.Usuario;
import com.minibanca.core.error.SaldoInsuficienteException;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.UsuarioRepository;
import com.minibanca.core.service.CacheCuentas;
import com.minibanca.core.service.ModoBloqueo;
import com.minibanca.core.service.TransferenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST DE INTEGRACIÓN: la aplicación ENTERA + PostgreSQL de verdad (Testcontainers).
 * Automatiza la prueba de concurrencia de la lección 4 (aquella con el script de PowerShell):
 *
 *   Una cuenta con 100 €. 10 transferencias de 15 € salen A LA VEZ.
 *   Solo caben 6 (90 €). Las otras 4 deben fallar por saldo. Al final: 10 € y 90 €.
 *   Si los cerrojos no funcionaran, saldrían más de 6 y el saldo quedaría negativo o descuadrado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TransferenciaConcurrenciaIntegracionTest {

    @Autowired TransferenciaService transferencias;
    @Autowired UsuarioRepository usuarios;
    @Autowired CuentaRepository cuentas;

    @MockitoBean CacheCuentas cache;   // sin Redis en los tests

    @Test
    void diezTransferenciasALaVezNoDescuadranNiUnCentimo() throws Exception {
        Usuario ana = usuarios.save(new Usuario("ana-" + UUID.randomUUID() + "@test.com", "hash", "Ana", Rol.USER));
        Cuenta origen = new Cuenta(ana, ibanAleatorio(), "Origen", "EUR");
        origen.acreditar(new BigDecimal("100.00"));
        origen = cuentas.save(origen);
        Cuenta destino = cuentas.save(new Cuenta(ana, ibanAleatorio(), "Destino", "EUR"));

        Long origenId = origen.getId();
        String ibanDestino = destino.getIban();

        ExecutorService hilos = Executors.newFixedThreadPool(10);
        CountDownLatch pistoletazo = new CountDownLatch(1);   // para que salgan TODAS a la vez
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            resultados.add(hilos.submit(() -> {
                pistoletazo.await();
                try {
                    transferencias.transferir(ana.getId(), UUID.randomUUID().toString(),
                            new CrearTransferenciaRequest(origenId, ibanDestino, new BigDecimal("15.00"), "Carrera"),
                            ModoBloqueo.PESIMISTA, false);
                    return true;
                } catch (SaldoInsuficienteException e) {
                    return false;
                }
            }));
        }
        pistoletazo.countDown();

        int correctas = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get()) correctas++;
        }
        hilos.shutdown();

        assertThat(correctas).isEqualTo(6);
        assertThat(cuentas.findById(origenId).orElseThrow().getSaldo()).isEqualByComparingTo("10.00");
        assertThat(cuentas.findById(destino.getId()).orElseThrow().getSaldo()).isEqualByComparingTo("90.00");
    }

    private static String ibanAleatorio() {
        StringBuilder sb = new StringBuilder("ES");
        for (int i = 0; i < 22; i++) sb.append(ThreadLocalRandom.current().nextInt(10));
        return sb.toString();
    }
}
