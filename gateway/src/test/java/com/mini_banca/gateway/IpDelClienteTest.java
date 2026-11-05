package com.mini_banca.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/** ¿A qué "cubo de fichas" del limitador va cada petición? */
class IpDelClienteTest {

    private final KeyResolver resolver = new LimitadoresConfig().ipDelCliente();

    @Test
    void usaLaIpRealQueMandaNginx() {
        var peticion = MockServerHttpRequest.get("/api/v1/cuentas")
                .header("X-Real-IP", "203.0.113.7")
                .remoteAddress(new InetSocketAddress("172.18.0.5", 50000))   // nginx
                .build();

        assertThat(resolver.resolve(MockServerWebExchange.from(peticion)).block()).isEqualTo("203.0.113.7");
    }

    @Test
    void sinNginxDelanteUsaLaIpDeLaConexion() {
        var peticion = MockServerHttpRequest.get("/api/v1/cuentas")
                .remoteAddress(new InetSocketAddress("192.168.1.20", 50000))
                .build();

        assertThat(resolver.resolve(MockServerWebExchange.from(peticion)).block()).isEqualTo("192.168.1.20");
    }
}
