package com.minibanca.core.service;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.api.dto.TransferenciaResponse;
import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.EstadoTransferencia;
import com.minibanca.core.domain.Movimiento;
import com.minibanca.core.domain.TipoMovimiento;
import com.minibanca.core.domain.Transferencia;
import com.minibanca.core.error.ConflictoException;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.error.ReglaNegocioException;
import com.minibanca.core.error.SaldoInsuficienteException;
import com.minibanca.core.repository.CuentasDeTransferencia;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.MovimientoRepository;
import com.minibanca.core.repository.TransferenciaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TransferenciaService {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final AuditoriaService auditoria;
    private final CacheCuentas cache;

    public TransferenciaService(CuentaRepository cuentaRepository,
                                MovimientoRepository movimientoRepository,
                                TransferenciaRepository transferenciaRepository,
                                AuditoriaService auditoria,
                                CacheCuentas cache) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.transferenciaRepository = transferenciaRepository;
        this.auditoria = auditoria;
        this.cache = cache;
    }

    /**
     * TODO lo de dentro ocurre en UNA transacción: o se hace entero, o no se hace nada.
     * Lección 5: ya no hay ResponseStatusException; lanzamos excepciones de DOMINIO y el
     * GlobalExceptionHandler decide el código HTTP.
     */
    @Transactional
    public ResultadoTransferencia transferir(Long usuarioId, String idempotencyKey,
                                             CrearTransferenciaRequest request,
                                             ModoBloqueo modo, boolean simularFallo) {
        // 1. La cuenta origen tiene que ser del usuario (RF-1.5)
        if (!cuentaRepository.existsByIdAndUsuarioId(request.cuentaOrigenId(), usuarioId)) {
            throw new RecursoNoEncontradoException("Cuenta origen no encontrada");
        }

        // 2. Buscar la cuenta destino por IBAN
        Long destinoId = cuentaRepository.findIdByIban(request.ibanDestino())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta destino no encontrada"));
        if (destinoId.equals(request.cuentaOrigenId())) {
            throw new ReglaNegocioException("Origen y destino deben ser cuentas distintas");
        }

        // 3. Bloquear las dos cuentas, siempre en el mismo orden (evita deadlocks)
        Long primeroId = Math.min(request.cuentaOrigenId(), destinoId);
        Long segundoId = Math.max(request.cuentaOrigenId(), destinoId);
        Cuenta primera = cargar(primeroId, modo);
        Cuenta segunda = cargar(segundoId, modo);
        Cuenta origen = primera.getId().equals(request.cuentaOrigenId()) ? primera : segunda;
        Cuenta destino = (origen == primera) ? segunda : primera;

        // 4. IDEMPOTENCIA (RF-3.5). Lo comprobamos DESPUÉS de bloquear: si llegan dos
        //    peticiones repetidas a la vez, hacen cola en el cerrojo y la segunda ya
        //    encuentra la transferencia de la primera.
        Optional<Transferencia> previa = transferenciaRepository.findByIdempotencyKey(idempotencyKey);
        if (previa.isPresent()) {
            Transferencia t = previa.get();
            if (!esLaMismaOperacion(t, request)) {
                throw new ConflictoException("Esta Idempotency-Key ya se usó para una transferencia distinta");
            }
            return new ResultadoTransferencia(TransferenciaResponse.from(t), true);   // NO se repite nada
        }

        // 5. Reglas de negocio -> 422
        if (!origen.isActiva() || !destino.isActiva()) {
            throw new ReglaNegocioException("Las dos cuentas deben estar activas");
        }
        if (!origen.getDivisa().equals(destino.getDivisa())) {
            throw new ReglaNegocioException("Las cuentas deben tener la misma divisa");
        }

        // 6. Mover el dinero. Si no hay saldo, la propia cuenta lanza SaldoInsuficienteException.
        origen.debitar(request.importe());
        movimientoRepository.save(new Movimiento(origen, TipoMovimiento.CARGO, request.importe(),
                "Transferencia a " + destino.getIban()));

        if (simularFallo) {
            // Lección 8: este registro va en su PROPIA transacción (REQUIRES_NEW), así que se queda
            // guardado aunque justo después hagamos rollback de la transferencia. Compruébalo en
            // /api/admin/auditoria: aparece TRANSFERENCIA_FALLIDA, pero el saldo no ha cambiado.
            auditoria.registrar(usuarioId, AccionAuditoria.TRANSFERENCIA_FALLIDA,
                    "Fallo simulado tras debitar " + request.importe() + " de la cuenta " + origen.getIban());
            throw new IllegalStateException("Fallo simulado después del débito");
        }

        destino.acreditar(request.importe());
        movimientoRepository.save(new Movimiento(destino, TipoMovimiento.ABONO, request.importe(),
                "Transferencia de " + origen.getIban()));

        Transferencia transferencia = transferenciaRepository.save(
                new Transferencia(origen, destino, request.importe(), request.concepto(), idempotencyKey));

        // Lección 11: han cambiado los saldos de DOS usuarios (el que envía y el que recibe)
        cache.olvidarTrasCommit(origen.getUsuario().getId(), destino.getUsuario().getId());

        return new ResultadoTransferencia(TransferenciaResponse.from(transferencia), false);
    }

    /** Lección 8: total transferido HOY (hora de Madrid) por el usuario, para el límite diario. */
    @Transactional(readOnly = true)
    public BigDecimal totalTransferidoHoy(Long usuarioId) {
        Instant inicioDelDia = LocalDate.now(ZoneId.of("Europe/Madrid"))
                .atStartOfDay(ZoneId.of("Europe/Madrid")).toInstant();
        BigDecimal total = transferenciaRepository.totalTransferidoDesde(usuarioId, inicioDelDia);
        return total != null ? total : BigDecimal.ZERO;
    }

    // ===================== Lección 9 =====================

    /**
     * RF-3.6: un ADMIN anula una transferencia. En un banco NO se borra nada: se hace la
     * operación CONTRARIA (el destino devuelve el dinero al origen) con sus propios movimientos,
     * y la transferencia queda marcada como ANULADA. Así el histórico cuenta la verdad completa.
     *
     * Mismo patrón que transferir(): una transacción, cerrojos en orden de id, reglas -> 422.
     */
    @Transactional
    public TransferenciaResponse anular(Long adminId, Long transferenciaId, String motivo) {
        CuentasDeTransferencia ids = transferenciaRepository.findCuentasById(transferenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Transferencia no encontrada"));

        // 1. Cerrojo sobre las dos cuentas, en orden de id (igual que al transferir -> sin deadlocks)
        Long primeroId = Math.min(ids.origenId(), ids.destinoId());
        Long segundoId = Math.max(ids.origenId(), ids.destinoId());
        Cuenta primera = cargar(primeroId, ModoBloqueo.PESIMISTA);
        Cuenta segunda = cargar(segundoId, ModoBloqueo.PESIMISTA);
        Cuenta origen = primera.getId().equals(ids.origenId()) ? primera : segunda;
        Cuenta destino = (origen == primera) ? segunda : primera;

        // 2. Ahora sí, la transferencia (también con cerrojo). Si dos admins anulan a la vez,
        //    el segundo espera y aquí ya ve estado = ANULADA -> 409.
        Transferencia t = transferenciaRepository.findByIdForUpdate(transferenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Transferencia no encontrada"));
        t.marcarAnulada(adminId, motivo);

        // 3. Reglas de negocio
        if (!origen.isActiva()) {
            throw new ReglaNegocioException("No se puede anular: la cuenta origen " + origen.getIban()
                    + " está cerrada y no puede recibir la devolución");
        }
        try {
            destino.debitar(t.getImporte());
        } catch (SaldoInsuficienteException e) {
            throw new ReglaNegocioException("No se puede anular: la cuenta destino " + destino.getIban()
                    + " ya no tiene saldo suficiente para devolver " + t.getImporte());
        }

        // 4. Devolver el dinero, dejando rastro en los dos históricos
        movimientoRepository.save(new Movimiento(destino, TipoMovimiento.CARGO, t.getImporte(),
                "Anulación de la transferencia " + t.getId()));
        origen.acreditar(t.getImporte());
        movimientoRepository.save(new Movimiento(origen, TipoMovimiento.ABONO, t.getImporte(),
                "Devolución de la transferencia " + t.getId()));

        cache.olvidarTrasCommit(origen.getUsuario().getId(), destino.getUsuario().getId());
        auditoria.registrar(adminId, AccionAuditoria.TRANSFERENCIA_ANULADA,
                "Transferencia " + t.getId() + " (" + t.getImporte() + " de " + origen.getIban()
                        + " a " + destino.getIban() + ") anulada. Motivo: " + motivo);

        return TransferenciaResponse.from(t);
    }

    /**
     * Listado paginado de transferencias ENVIADAS.
     *  - usuarioId != null -> solo las de ese usuario (lo usa "mis transferencias" y el filtro del admin)
     *  - usuarioId == null -> todas (solo admin; el orquestador garantiza que solo llega un ADMIN)
     */
    @Transactional(readOnly = true)
    public Page<TransferenciaResponse> buscar(Long usuarioId, EstadoTransferencia estado, Pageable pageable) {
        Specification<Transferencia> filtros = (root, query, cb) -> {
            List<Predicate> c = new ArrayList<>();
            if (usuarioId != null) c.add(cb.equal(root.get("cuentaOrigen").get("usuario").get("id"), usuarioId));
            if (estado != null) c.add(cb.equal(root.get("estado"), estado));
            return cb.and(c.toArray(new Predicate[0]));
        };
        return transferenciaRepository.findAll(filtros, pageable).map(TransferenciaResponse::from);
    }

    private boolean esLaMismaOperacion(Transferencia t, CrearTransferenciaRequest r) {
        return t.getCuentaOrigen().getId().equals(r.cuentaOrigenId())
                && t.getCuentaDestino().getIban().equals(r.ibanDestino())
                && t.getImporte().compareTo(r.importe()) == 0;
    }

    private Cuenta cargar(Long id, ModoBloqueo modo) {
        Optional<Cuenta> cuenta = (modo == ModoBloqueo.PESIMISTA)
                ? cuentaRepository.findByIdForUpdate(id)
                : cuentaRepository.findById(id);
        return cuenta.orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"));
    }
}
