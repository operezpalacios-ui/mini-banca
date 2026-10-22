package com.minibanca.core.repository;

import com.minibanca.core.domain.Movimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Lección 9: JpaSpecificationExecutor añade findAll(Specification, Pageable), que usamos para
 * los FILTROS opcionales del histórico (RF-4.2), igual que hicimos con la auditoría.
 */
public interface MovimientoRepository extends JpaRepository<Movimiento, Long>, JpaSpecificationExecutor<Movimiento> {

    Page<Movimiento> findByCuentaId(Long cuentaId, Pageable pageable);
}
