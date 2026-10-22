package com.minibanca.core.config;

import com.minibanca.core.api.dto.CuentaLigeraResponse;
import com.minibanca.core.api.dto.ResumenMensualResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Lección 11: CACHÉ con Redis.
 *
 * Una caché es una "chuleta": la primera vez que alguien pide algo, lo calculamos en la BBDD y
 * apuntamos el resultado en Redis (que vive en memoria y responde en ~1 ms). Las siguientes veces,
 * leemos la chuleta y la BBDD ni se entera. El problema difícil NO es guardar: es BORRAR la chuleta
 * cuando el dato real cambia (si no, enseñaríamos un saldo viejo). Eso lo hace CacheCuentas.
 *
 * @EnableCaching activa las anotaciones @Cacheable de los servicios.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CUENTAS_LIGERAS = "cuentasLigeras";
    public static final String RESUMEN_MENSUAL = "resumenMensual";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Cada caché con su configuración:
     *  - TTL (time to live): aunque nadie la borre, la chuleta CADUCA sola. Red de seguridad.
     *  - Cómo se guarda el valor: en JSON (lo podrás leer en la consola de Upstash).
     *  - Prefijo "minibanca:" en todas las claves, p. ej.  minibanca:cuentasLigeras::1
     */
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory conexion) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(nombre -> "minibanca:" + nombre + "::")
                .disableCachingNullValues();

        return RedisCacheManager.builder(conexion)
                .withCacheConfiguration(CUENTAS_LIGERAS, base
                        .entryTtl(Duration.ofMinutes(5))
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new ListaJsonRedisSerializer<>(CuentaLigeraResponse[].class))))
                .withCacheConfiguration(RESUMEN_MENSUAL, base
                        .entryTtl(Duration.ofMinutes(10))
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new ListaJsonRedisSerializer<>(ResumenMensualResponse[].class))))
                .build();
    }

    /**
     * ⭐ La caché es una AYUDA, no una pieza imprescindible. Si Redis se cae, NO queremos que la
     * app entera dé error 500: registramos el problema y seguimos como si no hubiera caché
     * (se va directo a la BBDD, más lento pero correcto). Sin esto, Spring lanzaría la excepción.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Caché '{}' no disponible al LEER la clave {}: voy a la BBDD. ({})",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Caché '{}' no disponible al GUARDAR la clave {}. ({})", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.error("No se pudo BORRAR la clave {} de la caché '{}': podría servir datos viejos hasta que caduque.",
                        key, cache.getName(), e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("No se pudo VACIAR la caché '{}'.", cache.getName(), e);
            }
        };
    }
}
