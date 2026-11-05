package com.mini_banca.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API GATEWAY de Mini banca (Spring Cloud Gateway).
 *
 *   navegador ──► nginx (:80) ──/api/──► GATEWAY (:8082) ──► orquestador (:8081) ──► core ...
 *
 * Es la "puerta de entrada" de la API. NO tiene lógica de negocio ni valida el JWT (eso sigue
 * siendo cosa del orquestador, que es el único que conoce la clave). Lo que hace es lo que
 * conviene hacer UNA vez y lo antes posible, para todas las rutas:
 *   - Enrutar /api/** al orquestador                                    (RutasConfig)
 *   - Limitar peticiones por IP, más estricto en login/registro          (LimitadoresConfig)
 *   - Circuit breaker + timeouts: si el orquestador cae, 503 al instante (CircuitBreakerConfig)
 *   - Dar a cada petición su traceId (X-Trace-Id) desde la primera capa  (TraceIdFilter)
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
