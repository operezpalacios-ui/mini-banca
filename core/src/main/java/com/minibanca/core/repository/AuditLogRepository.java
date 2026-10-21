package com.minibanca.core.repository;

import com.minibanca.core.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor permite construir la consulta "a trozos" en tiempo de ejecución:
 * útil cuando los filtros son opcionales (usuario, acción, fechas...).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
