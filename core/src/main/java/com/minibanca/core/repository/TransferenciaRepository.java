package com.minibanca.core.repository;

import com.minibanca.core.domain.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

    Optional<Transferencia> findByIdempotencyKey(String idempotencyKey);

    /** Lección 8: cuánto ha transferido un usuario desde una fecha (para el límite diario). null si nada. */
    @Query("select sum(t.importe) from Transferencia t where t.cuentaOrigen.usuario.id = :usuarioId and t.fecha >= :desde")
    BigDecimal totalTransferidoDesde(@Param("usuarioId") Long usuarioId, @Param("desde") Instant desde);
}
