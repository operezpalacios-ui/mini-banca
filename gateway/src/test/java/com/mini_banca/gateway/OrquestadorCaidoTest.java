package com.mini_banca.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Orquestador APAGADO (en el puerto 1 no escucha nadie): el gateway no debe colgarse ni
 * devolver un 500 feo, sino un 503 con el mismo formato que el resto de errores de la API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "orquestador.url=http://127.0.0.1:1")
@AutoConfigureWebTestClient
class OrquestadorCaidoTest {

    @Autowired
    WebTestClient web;

    @Test
    void responde503ConProblemDetailYTraceId() {
        web.post().uri("/api/v1/transferencias").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("X-Trace-Id")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Servicio no disponible")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.traceId").isNotEmpty();
    }
}
