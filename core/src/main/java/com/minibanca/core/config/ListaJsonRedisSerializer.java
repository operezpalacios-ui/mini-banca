package com.minibanca.core.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

/**
 * Convierte una List<T> en JSON para guardarla en Redis, y al revés.
 * Redis solo guarda BYTES: alguien tiene que traducir nuestros objetos Java a bytes.
 *
 * (Por defecto Spring usaría la serialización binaria de Java: funciona, pero en Redis verías
 * basura ilegible y además obliga a que las clases implementen Serializable. JSON se lee a simple vista.)
 */
public class ListaJsonRedisSerializer<T> implements RedisSerializer<List<T>> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Class<T[]> tipoArray;   // p. ej. CuentaLigeraResponse[].class

    public ListaJsonRedisSerializer(Class<T[]> tipoArray) {
        this.tipoArray = tipoArray;
    }

    @Override
    public byte[] serialize(List<T> valor) throws SerializationException {
        if (valor == null) return new byte[0];
        try {
            return JSON.writeValueAsBytes(valor);
        } catch (RuntimeException e) {
            throw new SerializationException("No se pudo convertir a JSON", e);
        }
    }

    @Override
    public List<T> deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return List.copyOf(Arrays.asList(JSON.readValue(bytes, tipoArray)));
        } catch (RuntimeException e) {
            throw new SerializationException("No se pudo leer el JSON de la caché", e);
        }
    }
}
