package com.minibanca.core.repository;

import com.minibanca.core.domain.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

    Optional<Transferencia> findByIdempotencyKey(String idempotencyKey);
}
