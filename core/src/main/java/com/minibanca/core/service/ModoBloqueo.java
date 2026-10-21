package com.minibanca.core.service;

/**
 * Solo para la lección 4: permite comparar las dos estrategias con la misma petición.
 * En una app real elegirías UNA y la documentarías (RF-3.4).
 */
public enum ModoBloqueo {
    /** @Version: no bloquea; si dos chocan, el segundo falla al guardar (409) */
    OPTIMISTA,
    /** SELECT ... FOR UPDATE: las peticiones hacen cola y van de una en una */
    PESIMISTA
}
