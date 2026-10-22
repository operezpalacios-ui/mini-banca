package com.minibanca.core.repository;

import com.minibanca.core.domain.Transferencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long>,
        JpaSpecificationExecutor<Transferencia> {

    Optional<Transferencia> findByIdempotencyKey(String idempotencyKey);

    /**
     * Lección 8: cuánto ha transferido un usuario desde una fecha (para el límite diario). null si nada.
     * Lección 9: las ANULADAS no cuentan (ese dinero ya volvió al cliente).
     */
    @Query("""
            select sum(t.importe) from Transferencia t
            where t.cuentaOrigen.usuario.id = :usuarioId
              and t.fecha >= :desde
              and t.estado = com.minibanca.core.domain.EstadoTransferencia.COMPLETADA
            """)
    BigDecimal totalTransferidoDesde(@Param("usuarioId") Long usuarioId, @Param("desde") Instant desde);

    /**
     * Lección 9: listado de transferencias. Sobrescribimos el findAll de Specification SOLO para
     * ponerle un @EntityGraph: TransferenciaResponse lee el IBAN de las dos cuentas y, sin esto,
     * tendríamos otra vez el N+1 de la lección 3 (2 consultas extra por cada transferencia).
     */
    @Override
    @EntityGraph(attributePaths = {"cuentaOrigen", "cuentaDestino"})
    Page<Transferencia> findAll(Specification<Transferencia> spec, Pageable pageable);

    /**
     * Lección 9 (anulación): solo los ids de las dos cuentas, SIN cargar la transferencia todavía.
     * Primero bloqueamos las cuentas y después leemos la transferencia con cerrojo: si la
     * leyéramos antes, Hibernate se quedaría con una copia que podría estar desfasada.
     */
    @Query("""
            select new com.minibanca.core.repository.CuentasDeTransferencia(t.cuentaOrigen.id, t.cuentaDestino.id)
            from Transferencia t where t.id = :id
            """)
    Optional<CuentasDeTransferencia> findCuentasById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transferencia t where t.id = :id")
    Optional<Transferencia> findByIdForUpdate(@Param("id") Long id);
}
