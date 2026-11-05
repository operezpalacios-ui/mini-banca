package com.mini_banca.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del gateway "de verdad": arranca el gateway entero y, en vez del orquestador, un
 * ORQUESTADOR FALSO (un servidor HTTP mínimo del propio JDK) que apunta lo que le llega.
 * Así comprobamos lo que importa: qué reenvía el gateway y con qué cabeceras.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Corto, para probar el 504 sin esperar 20 s... pero no TANTO que en una máquina de CI lenta
        // (la primera petición "calienta" Netty, Redis...) los tests normales acaben en 504.
        properties = "orquestador.timeout=3s")
@AutoConfigureWebTestClient
class GatewayTest {

    /** Lo que recibió el orquestador falso en la última petición. */
    static final Map<String, String> recibido = new ConcurrentHashMap<>();
    static final AtomicInteger llamadas = new AtomicInteger();
    static HttpServer orquestadorFalso;

    @DynamicPropertySource
    static void orquestador(DynamicPropertyRegistry registro) throws IOException {
        orquestadorFalso = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        orquestadorFalso.createContext("/", ex -> {
            llamadas.incrementAndGet();
            recibido.put("ruta", ex.getRequestURI().toString());
            recibido.put("metodo", ex.getRequestMethod());
            recibido.put("cuerpo", new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.getRequestHeaders().forEach((k, v) -> recibido.put("h:" + k.toLowerCase(), String.join(",", v)));

            if (ex.getRequestURI().getPath().equals("/api/v1/lento")) {
                try { Thread.sleep(6000); } catch (InterruptedException ignored) { }
            }
            // Como el orquestador real: devuelve el X-Trace-Id que le llegó
            String traza = ex.getRequestHeaders().getFirst("X-Trace-Id");
            if (traza != null) ex.getResponseHeaders().add("X-Trace-Id", traza);
            byte[] respuesta = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, respuesta.length);
            ex.getResponseBody().write(respuesta);
            ex.close();
        });
        // varios hilos: si no, la petición "lenta" bloquearía a todas las demás
        orquestadorFalso.setExecutor(Executors.newCachedThreadPool());
        orquestadorFalso.start();
        registro.add("orquestador.url", () -> "http://127.0.0.1:" + orquestadorFalso.getAddress().getPort());
    }

    @AfterAll
    static void parar() {
        orquestadorFalso.stop(0);
    }

    @Autowired
    WebTestClient web;

    @BeforeEach
    void limpiar() {
        recibido.clear();
        llamadas.set(0);
    }

    @Test
    void reenviaLaPeticionAlOrquestadorSinCambiarLaRuta() {
        web.get().uri("/api/v1/cuentas?pagina=2").header("Authorization", "Bearer abc")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.ok").isEqualTo(true);

        assertThat(recibido).containsEntry("ruta", "/api/v1/cuentas?pagina=2")
                .containsEntry("metodo", "GET")
                // el JWT pasa tal cual: lo valida el orquestador, no el gateway
                .containsEntry("h:authorization", "Bearer abc");
    }

    @Test
    void reenviaElCuerpoDelLogin() {
        web.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.com\",\"password\":\"x\"}")
                .exchange()
                .expectStatus().isOk();

        assertThat(recibido).containsEntry("metodo", "POST")
                .containsEntry("cuerpo", "{\"email\":\"a@b.com\",\"password\":\"x\"}");
    }

    @Test
    void generaUnTraceIdYLoPasaAlOrquestador() {
        var respuesta = web.get().uri("/api/v1/cuentas").exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        var cabeceras = respuesta.getResponseHeaders().get(TraceIdFilter.CABECERA);
        assertThat(cabeceras).hasSize(1);   // una sola, aunque el orquestador también la devuelva
        assertThat(cabeceras.getFirst()).matches("[a-f0-9]{32}")
                .isEqualTo(recibido.get("h:x-trace-id"));
    }

    @Test
    void respetaUnTraceIdValidoYCambiaUnoInvalido() {
        web.get().uri("/api/v1/cuentas").header("X-Trace-Id", "mi-traza-1234")
                .exchange()
                .expectHeader().valueEquals("X-Trace-Id", "mi-traza-1234");
        assertThat(recibido).containsEntry("h:x-trace-id", "mi-traza-1234");

        web.get().uri("/api/v1/cuentas").header("X-Trace-Id", "corto")
                .exchange()
                .expectHeader().value("X-Trace-Id", v -> assertThat(v).isNotEqualTo("corto"));
        assertThat(recibido.get("h:x-trace-id")).isNotEqualTo("corto");
    }

    /** El bug de la lección 16: si el gateway tocara estas cabeceras, el login daría 403 CORS. */
    @Test
    void dejaPasarTalCualLasCabecerasXForwardedDeNginx() {
        web.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "xxx-4200.app.github.dev")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("Forwarded", "proto=http;host=web-falsa.com")   // esta NO debe pasar
                .exchange()
                .expectStatus().isOk();

        assertThat(recibido).containsEntry("h:x-forwarded-proto", "https")
                .containsEntry("h:x-forwarded-host", "xxx-4200.app.github.dev")
                .containsEntry("h:x-forwarded-for", "203.0.113.7")
                .doesNotContainKey("h:x-forwarded-port")
                .doesNotContainKey("h:forwarded");
    }

    @Test
    void loQueNoEsApiNoSeReenvia() {
        web.get().uri("/actuator/prometheus-del-orquestador").exchange().expectStatus().isNotFound();
        web.get().uri("/cualquier-cosa").exchange().expectStatus().isNotFound();
        assertThat(llamadas.get()).isZero();
    }

    @Test
    void siElOrquestadorTardaDemasiadoResponde504ConProblemDetail() {
        web.get().uri("/api/v1/lento").exchange()
                .expectStatus().isEqualTo(504)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Tiempo de espera agotado")
                .jsonPath("$.traceId").isNotEmpty();
    }
}
