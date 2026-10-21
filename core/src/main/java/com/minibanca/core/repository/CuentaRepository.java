package com.minibanca.core.repository;

import com.minibanca.core.domain.Cuenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * No hay que implementar nada: Spring Data genera el SQL a partir del NOMBRE del método.
 *   findByUsuarioId        -> ... WHERE usuario_id = ?
 *   findByIdAndUsuarioId   -> ... WHERE id = ? AND usuario_id = ?
 *   existsByIban           -> SELECT count(*) > 0 ... WHERE iban = ?
 */
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Page<Cuenta> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Buscar SIEMPRE por id + dueño: así un usuario nunca puede ver la cuenta de otro (RF-1.5)
    Optional<Cuenta> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByIban(String iban);
}
