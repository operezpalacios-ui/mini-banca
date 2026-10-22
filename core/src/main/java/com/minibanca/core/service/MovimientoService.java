package com.minibanca.core.service;

import com.minibanca.core.api.dto.MovimientoResponse;
import com.minibanca.core.domain.Movimiento;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.error.ReglaNegocioException;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.MovimientoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Service
@Transactional(readOnly = true)
public class MovimientoService {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final int TAMANO_LOTE_EXPORTACION = 500;

    private final MovimientoRepository movimientoRepository;
    private final CuentaRepository cuentaRepository;

    public MovimientoService(MovimientoRepository movimientoRepository, CuentaRepository cuentaRepository) {
        this.movimientoRepository = movimientoRepository;
        this.cuentaRepository = cuentaRepository;
    }

    /** Histórico de una cuenta (RF-4.1) con filtros opcionales (RF-4.2). */
    public Page<MovimientoResponse> historico(Long usuarioId, Long cuentaId, MovimientoFiltro filtro, Pageable pageable) {
        comprobarPropietario(usuarioId, cuentaId);
        validar(filtro);
        return movimientoRepository.findAll(especificacion(cuentaId, filtro, null), pageable)
                .map(MovimientoResponse::from);
    }

    /**
     * RF-4.3: exportación. NO cargamos todos los movimientos en memoria (podrían ser 100.000):
     * los leemos en LOTES de 500 y cada lote se entrega a "procesarLote" (que lo escribe en el CSV)
     * antes de pedir el siguiente. La memoria usada es la misma para 10 filas que para 1 millón.
     *
     * Paginación por CLAVE ("keyset"): en lugar de "dame la página 7", pedimos "los 500 siguientes
     * con id mayor que el último que vi". Si mientras exportamos entra un movimiento nuevo, con
     * páginas numeradas se desplazaría todo y saldrían filas repetidas; así, no.
     *
     * Sin @Transactional a propósito (propagation NOT_SUPPORTED): cada lote es una consulta
     * cortita e independiente, y no tenemos una conexión de BBDD ocupada durante toda la descarga.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void exportar(Long usuarioId, Long cuentaId, MovimientoFiltro filtro,
                         Consumer<List<MovimientoResponse>> procesarLote) {
        Long ultimoId = 0L;
        while (true) {
            List<Movimiento> lote = movimientoRepository.findAll(
                    especificacion(cuentaId, filtro, ultimoId),
                    PageRequest.of(0, TAMANO_LOTE_EXPORTACION, Sort.by("id"))).getContent();
            if (lote.isEmpty()) {
                return;
            }
            procesarLote.accept(lote.stream().map(MovimientoResponse::from).toList());
            if (lote.size() < TAMANO_LOTE_EXPORTACION) {
                return;
            }
            ultimoId = lote.get(lote.size() - 1).getId();
        }
    }

    /**
     * Lo llama el controller ANTES de empezar a enviar el CSV, para poder responder un 404/422 "limpio".
     * Devuelve el IBAN, que usamos para el nombre del fichero.
     */
    public String prepararExportacion(Long usuarioId, Long cuentaId, MovimientoFiltro filtro) {
        validar(filtro);
        return cuentaRepository.findByIdAndUsuarioId(cuentaId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"))
                .getIban();
    }

    // ===================== Utilidades =====================

    private void comprobarPropietario(Long usuarioId, Long cuentaId) {
        if (!cuentaRepository.existsByIdAndUsuarioId(cuentaId, usuarioId)) {
            throw new RecursoNoEncontradoException("Cuenta no encontrada");
        }
    }

    private void validar(MovimientoFiltro f) {
        if (f.desde() != null && f.hasta() != null && f.desde().isAfter(f.hasta())) {
            throw new ReglaNegocioException("'desde' no puede ser posterior a 'hasta'");
        }
        if (f.importeMin() != null && f.importeMax() != null && f.importeMin().compareTo(f.importeMax()) > 0) {
            throw new ReglaNegocioException("'importeMin' no puede ser mayor que 'importeMax'");
        }
        if (f.texto() != null && f.texto().length() > 100) {
            throw new ReglaNegocioException("El texto a buscar no puede superar los 100 caracteres");
        }
    }

    /**
     * Construye el WHERE pieza a pieza: solo añade las condiciones de los filtros que vienen rellenos.
     * Todo va como PARÁMETROS de la consulta (nunca concatenando texto en el SQL): así es imposible
     * una inyección SQL aunque alguien busque  texto=' OR 1=1 --
     */
    private Specification<Movimiento> especificacion(Long cuentaId, MovimientoFiltro f, Long idMayorQue) {
        return (root, query, cb) -> {
            List<Predicate> c = new ArrayList<>();
            c.add(cb.equal(root.get("cuenta").get("id"), cuentaId));
            if (f.desde() != null) {
                c.add(cb.greaterThanOrEqualTo(root.<Instant>get("fecha"), inicioDelDia(f.desde())));
            }
            if (f.hasta() != null) {
                // "hasta el 31" = "antes del día 1 a las 00:00" -> incluye el 31 entero
                c.add(cb.lessThan(root.<Instant>get("fecha"), inicioDelDia(f.hasta().plusDays(1))));
            }
            if (f.tipo() != null) {
                c.add(cb.equal(root.get("tipo"), f.tipo()));
            }
            if (f.importeMin() != null) {
                c.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("importe"), f.importeMin()));
            }
            if (f.importeMax() != null) {
                c.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("importe"), f.importeMax()));
            }
            if (f.texto() != null && !f.texto().isBlank()) {
                // Búsqueda sin distinguir mayúsculas. Escapamos % y _ para que se busquen tal cual.
                String patron = "%" + escaparLike(f.texto().trim().toLowerCase(Locale.ROOT)) + "%";
                c.add(cb.like(cb.lower(root.<String>get("concepto")), patron, '\\'));
            }
            if (idMayorQue != null) {
                c.add(cb.greaterThan(root.<Long>get("id"), idMayorQue));
            }
            return cb.and(c.toArray(new Predicate[0]));
        };
    }

    private static Instant inicioDelDia(java.time.LocalDate dia) {
        return dia.atStartOfDay(MADRID).toInstant();
    }

    private static String escaparLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
