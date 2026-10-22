package com.minibanca.core.service;

import com.minibanca.core.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * INVALIDACIÓN de la caché: "esta chuleta ya no vale, bórrala".
 *
 * ⭐ ¿Por qué DESPUÉS del commit y no en el momento?
 *   Si borramos la chuleta mientras la transacción aún está abierta, otra petición podría leer
 *   la BBDD justo en ese instante (todavía con el saldo VIEJO, porque no hemos hecho commit),
 *   y volver a apuntar el saldo viejo en la caché. Resultado: chuleta equivocada durante 5 min.
 *   Borrando tras el commit, quien lea después ya ve el dato nuevo.
 *   (Y si la transacción hace ROLLBACK, no se borra nada: no ha cambiado nada.)
 */
@Component
public class CacheCuentas {

    private static final Logger log = LoggerFactory.getLogger(CacheCuentas.class);

    private final CacheManager cacheManager;

    public CacheCuentas(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /** Han cambiado saldos o cuentas de estos usuarios. */
    public void olvidarTrasCommit(Long... usuarioIds) {
        Runnable borrar = () -> {
            Cache ligeras = cacheManager.getCache(CacheConfig.CUENTAS_LIGERAS);
            Cache resumen = cacheManager.getCache(CacheConfig.RESUMEN_MENSUAL);
            try {
                for (Long id : usuarioIds) {
                    if (ligeras != null) ligeras.evict(id);   // borrado EXACTO: solo la clave de ese usuario
                }
                // El resumen tiene claves "usuario:cuenta:meses"; para no complicarnos lo vaciamos
                // entero. Es un compromiso: más sencillo, pero los demás usuarios pierden su chuleta.
                if (resumen != null) resumen.clear();
            } catch (RuntimeException e) {
                // Redis caído: no rompemos la operación (el dinero YA se movió bien). Caducará sola.
                log.error("No se pudo invalidar la caché de los usuarios {}", (Object) usuarioIds, e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar.run();
                }
            });
        } else {
            borrar.run();
        }
    }
}
