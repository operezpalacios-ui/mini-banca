package com.mini_banca.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El limitador con un REDIS DE VERDAD (Testcontainers lo arranca en Docker).
 * En tu ordenador sin Docker este test se SALTA; en GitHub Actions y Jenkins sí se ejecuta.
 *
 * Login con un cubo de 2 fichas, 2 fichas por intento y 1 ficha/s de relleno: el 1º pasa y
 * vacía el cubo; el 2º (enseguida) -> 429, porque hacen falta 2 s para volver a tener 2 fichas.
 * (El orquestador está "apagado": los que pasan reciben 503; da igual, aquí miramos el limitador.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "orquestador.url=http://127.0.0.1:1",
        "limites.auth.fichas-por-segundo=1",
        "limites.auth.burst=2",
        "limites.auth.fichas-por-peticion=2"})
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
class LimitadorRedisTest {

    @Container
    @ServiceConnection(name = "redis")   // Spring lee del contenedor dónde está Redis y se conecta solo
    static final GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Autowired
    WebTestClient web;

    @Test
    void unSegundoLoginSeguidoDesdeLaMismaIpRecibe429() {
        login("198.51.100.1").expectStatus().isEqualTo(503);
        login("198.51.100.1").expectStatus().isEqualTo(429)
                .expectHeader().exists("X-Trace-Id")
                // (no miramos el valor exacto: Redis cuenta en segundos enteros y puede valer 0 o 1
                //  según las dos peticiones caigan en el mismo segundo o no)
                .expectHeader().exists("X-RateLimit-Remaining");

        // Otra IP tiene su propio cubo: a ella no le afecta
        login("198.51.100.2").expectStatus().isEqualTo(503);
    }

    private WebTestClient.ResponseSpec login(String ip) {
        return web.post().uri("/api/v1/auth/login")
                .header("X-Real-IP", ip)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange();
    }
}
