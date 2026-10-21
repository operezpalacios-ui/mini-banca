package com.minibanca.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class CriptografiaConfig {

    /**
     * BCrypt (RF-1.1): convierte "ana-12345" en algo como "$2a$10$thIv4KMB3B6X...".
     *  - Es de UN SOLO SENTIDO: no se puede "descifrar", solo comprobar si una contraseña encaja.
     *  - Lleva SAL aleatoria: dos usuarios con la misma contraseña tienen hashes distintos.
     *  - Es LENTO a propósito (coste 10 = 2^10 rondas): probar millones de contraseñas sale carísimo.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
