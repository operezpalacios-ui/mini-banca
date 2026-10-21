package com.minibanca.core.service;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.api.dto.TransferenciaResponse;
import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.Movimiento;
import com.minibanca.core.domain.TipoMovimiento;
import com.minibanca.core.domain.Transferencia;
import com.minibanca.core.error.ConflictoException;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.error.ReglaNegocioException;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.MovimientoRepository;
import com.minibanca.core.repository.TransferenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class TransferenciaService {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final AuditoriaService auditoria;

    public TransferenciaService(CuentaRepository cuentaRepository,
                                MovimientoRepository movimientoRepository,
                                TransferenciaRepository transferenciaRepository,
                                AuditoriaService auditoria) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.transferenciaRepository = transferenciaRepository;
        this.auditoria = auditoria;
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
