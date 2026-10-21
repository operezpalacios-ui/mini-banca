package com.minibanca.core.service;

import com.minibanca.core.api.dto.AuditoriaResponse;
import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.domain.AuditLog;
import com.minibanca.core.error.TraceIdFilter;
import com.minibanca.core.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Auditoría (RF-6.3).
 *
 * ⭐ Propagation.REQUIRES_NEW (RF-7.4): cada registro se guarda en una transacción PROPIA e
 * independiente. Si la operación que lo llama falla y hace rollback, el registro de auditoría
 * SE QUEDA. Justo lo que queremos: "alguien intentó X y falló" también debe constar.
 *
 * ⚠️ Por eso esto es un servicio APARTE. Si registrar() fuera un método privado dentro de
 * TransferenciaService, Spring ignoraría el REQUIRES_NEW (fallo por "autoinvocación"):
 * las anotaciones @Transactional solo funcionan cuando la llamada entra desde OTRO bean,
 * porque Spring las aplica mediante un "proxy" que envuelve al objeto.
 */
@Service
public class AuditoriaService {

    private final AuditLogRepository repo;

    public AuditoriaService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long usuarioId, AccionAuditoria accion, String detalle) {
        String recortado = (detalle != null && detalle.length() > 500) ? detalle.substring(0, 500) : detalle;
        repo.save(new AuditLog(usuarioId, accion, recortado, MDC.get(TraceIdFilter.MDC_KEY)));
    }

    /** Búsqueda con filtros OPCIONALES: solo se añade al WHERE lo que venga relleno. */
    @Transactional(readOnly = true)
    public Page<AuditoriaResponse> buscar(Long usuarioId, AccionAuditoria accion, Instant desde, Instant hasta,
                                          Pageable pageable) {
        Specification<AuditLog> filtros = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (usuarioId != null) condiciones.add(cb.equal(root.get("usuarioId"), usuarioId));
            if (accion != null) condiciones.add(cb.equal(root.get("accion"), accion));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.<Instant>get("fecha"), desde));
            if (hasta != null) condiciones.add(cb.lessThan(root.<Instant>get("fecha"), hasta));
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
        return repo.findAll(filtros, pageable).map(AuditoriaResponse::from);
    }
}
