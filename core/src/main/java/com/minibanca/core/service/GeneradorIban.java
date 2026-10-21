package com.minibanca.core.service;

import com.minibanca.core.repository.CuentaRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genera IBAN ficticios con formato español: "ES" + 22 dígitos.
 * La unicidad la garantiza de verdad la restricción UNIQUE de la BBDD; aquí solo evitamos
 * repetir en el caso normal.
 */
@Component
public class GeneradorIban {

    private final SecureRandom random = new SecureRandom();
    private final CuentaRepository cuentaRepository;

    public GeneradorIban(CuentaRepository cuentaRepository) {
        this.cuentaRepository = cuentaRepository;
    }

    public String generar() {
        String iban;
        do {
            StringBuilder sb = new StringBuilder("ES");
            for (int i = 0; i < 22; i++) {
                sb.append(random.nextInt(10));
            }
            iban = sb.toString();
        } while (cuentaRepository.existsByIban(iban));
        return iban;
    }
}
