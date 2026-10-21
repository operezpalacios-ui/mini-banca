package com.minibanca.core.repository;

import com.minibanca.core.domain.Movimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    Page<Movimiento> findByCuentaId(Long cuentaId, Pageable pageable);
}
