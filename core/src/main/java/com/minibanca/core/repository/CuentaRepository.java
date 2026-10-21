package com.minibanca.core.repository;

import com.minibanca.core.domain.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByIban(String iban);

    // --- Lección 3: el problema N+1 ---

    // VERSIÓN LENTA: trae solo las cuentas. Los movimientos se cargarán después,
    // UNO A UNO, cuando alguien llame a cuenta.getMovimientos()  -> 1 + N consultas
    List<Cuenta> findByUsuarioIdOrderById(Long usuarioId);

    // VERSIÓN RÁPIDA: @EntityGraph le dice a Hibernate "trae también los movimientos
    // en la MISMA consulta" (un LEFT JOIN)  -> 1 consulta
    @EntityGraph(attributePaths = "movimientos")
    @Query("select c from Cuenta c where c.usuario.id = :usuarioId order by c.id")
    List<Cuenta> findConMovimientosByUsuarioId(@Param("usuarioId") Long usuarioId);

    // Alternativa equivalente con JPQL puro (JOIN FETCH):
    // @Query("select c from Cuenta c left join fetch c.movimientos where c.usuario.id = :usuarioId order by c.id")

    // --- Lección 4: transferencias ---

    // Solo el id (no la entidad entera): así no "contaminamos" la sesión de Hibernate
    // con una copia sin bloquear antes de pedirla con bloqueo.
    @Query("select c.id from Cuenta c where c.iban = :iban")
    Optional<Long> findIdByIban(@Param("iban") String iban);

    // BLOQUEO PESIMISTA: genera  SELECT ... FOR UPDATE
    // La fila queda "reservada" hasta que termine la transacción: si otra petición
    // intenta leerla con FOR UPDATE, ESPERA en la cola hasta que la primera acabe.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id")
    Optional<Cuenta> findByIdForUpdate(@Param("id") Long id);
}
