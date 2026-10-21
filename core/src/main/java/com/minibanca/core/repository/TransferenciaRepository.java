package com.minibanca.core.repository;

import com.minibanca.core.domain.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
}
