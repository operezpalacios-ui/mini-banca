package com.mini_banca.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * LIMITADOR DE PETICIONES (rate limiting) con el algoritmo del "cubo de fichas" (token bucket):
 *  - Cada IP tiene un cubo con capacidad para "burst" fichas.
 *  - Cada petición gasta "fichas-por-peticion" fichas; el cubo se rellena a "fichas-por-segundo".
 *  - Cubo vacío -> 429 Too Many Requests (con la cabecera X-RateLimit-Remaining para saberlo).
 *
 * Los cubos viven en REDIS (el mismo que usa el core para la caché): así, si mañana hay
 * 3 gateways, comparten los contadores y el límite sigue siendo el mismo.
 * Si Redis no responde, Spring Cloud Gateway DEJA PASAR la petición (y lo apunta en el log):
 * preferimos que la banca funcione sin limitador a que se caiga por culpa del limitador.
 */
@Configuration
public class LimitadoresConfig {

    /** Límite general para toda la API (por IP). Generoso: una pantalla hace varias llamadas a la vez. */
    @Primary
    @Bean
    RedisRateLimiter limitadorApi(@Value("${limites.api.fichas-por-segundo}") int porSegundo,
                                  @Value("${limites.api.burst}") int burst) {
        return new RedisRateLimiter(porSegundo, burst, 1);
    }

    /**
     * Límite ESTRICTO para login, registro y refresh: frena a quien prueba contraseñas a lo bruto.
     * Con los valores por defecto (1 ficha/s, cubo de 60, 6 fichas por petición):
     * 10 intentos seguidos y, después, 1 intento cada 6 segundos (10 por minuto).
     */
    @Bean
    RedisRateLimiter limitadorAuth(@Value("${limites.auth.fichas-por-segundo}") int porSegundo,
                                   @Value("${limites.auth.burst}") int burst,
                                   @Value("${limites.auth.fichas-por-peticion}") int porPeticion) {
        return new RedisRateLimiter(porSegundo, burst, porPeticion);
    }

    /**
     * ¿De QUIÉN es cada cubo? De la IP del cliente.
     *
     * Ojo: el gateway no ve la IP del navegador, ve la de nginx. Por eso nginx manda la IP real en
     * X-Real-IP (y la ESCRIBE él, así que un cliente no puede falsificarla: nginx pisa la suya).
     * Solo es seguro porque el gateway NO está publicado: solo nginx puede hablarle.
     * (No usamos el usuario del JWT: el gateway no valida el token y cualquiera podría inventarse uno.)
     */
    @Primary
    @Bean
    KeyResolver ipDelCliente() {
        return exchange -> {
            String ipReal = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (ipReal != null && !ipReal.isBlank()) {
                return Mono.just(ipReal.trim());
            }
            InetSocketAddress remota = exchange.getRequest().getRemoteAddress();
            return Mono.just(remota != null && remota.getAddress() != null
                    ? remota.getAddress().getHostAddress()
                    : "desconocida");
        };
    }
}
