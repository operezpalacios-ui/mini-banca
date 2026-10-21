package com.minibanca.core.service;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.api.dto.TransferenciaResponse;
import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.Movimiento;
import com.minibanca.core.domain.TipoMovimiento;
import com.minibanca.core.domain.Transferencia;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.MovimientoRepository;
import com.minibanca.core.repository.TransferenciaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransferenciaService {

    private static final HttpStatusCode REGLA_DE_NEGOCIO = HttpStatusCode.valueOf(422);

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final TransferenciaRepository transferenciaRepository;

    public TransferenciaService(CuentaRepository cuentaRepository,
                                MovimientoRepository movimientoRepository,
                                TransferenciaRepository transferenciaRepository) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.transferenciaRepository = transferenciaRepository;
    }

    /**
     * TODO lo de dentro ocurre en UNA transacción: o se hace entero, o no se hace nada.
     * Si salta cualquier RuntimeException, Spring hace ROLLBACK de todo (RF-3.3).
     */
    @Transactional
    public TransferenciaResponse transferir(Long usuarioId, CrearTransferenciaRequest request,
                                            ModoBloqueo modo, boolean simularFallo) {
        BigDecimal importe = request.importe();

        // 1. La cuenta origen tiene que ser del usuario (RF-1.5)
        if (!cuentaRepository.existsByIdAndUsuarioId(request.cuentaOrigenId(), usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta origen no encontrada");
        }

        // 2. Buscar la cuenta destino por IBAN (puede ser de otra persona)
        Long destinoId = cuentaRepository.findIdByIban(request.ibanDestino())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta destino no encontrada"));

        if (destinoId.equals(request.cuentaOrigenId())) {
            throw new ResponseStatusException(REGLA_DE_NEGOCIO, "Origen y destino deben ser cuentas distintas");
        }

        // 3. Cargar las dos cuentas SIEMPRE en el mismo orden (id menor primero).
        //    Con bloqueo pesimista esto evita el DEADLOCK: si A->B y B->A llegan a la vez,
        //    las dos intentan bloquear primero la misma cuenta, y una simplemente espera.
        Long primeroId = Math.min(request.cuentaOrigenId(), destinoId);
        Long segundoId = Math.max(request.cuentaOrigenId(), destinoId);
        Cuenta primera = cargar(primeroId, modo);
        Cuenta segunda = cargar(segundoId, modo);
        Cuenta origen = primera.getId().equals(request.cuentaOrigenId()) ? primera : segunda;
        Cuenta destino = (origen == primera) ? segunda : primera;

        // 4. Reglas de negocio (RF-3.2) -> 422
        if (!origen.isActiva() || !destino.isActiva()) {
            throw new ResponseStatusException(REGLA_DE_NEGOCIO, "Las dos cuentas deben estar activas");
        }
        if (!origen.getDivisa().equals(destino.getDivisa())) {
            throw new ResponseStatusException(REGLA_DE_NEGOCIO, "Las cuentas deben tener la misma divisa");
        }
        if (origen.getSaldo().compareTo(importe) < 0) {
            throw new ResponseStatusException(REGLA_DE_NEGOCIO, "Saldo insuficiente");
        }

        // 5. Mover el dinero
        origen.debitar(importe);
        movimientoRepository.save(new Movimiento(origen, TipoMovimiento.CARGO, importe,
                "Transferencia a " + destino.getIban()));

        // Solo para la lección: un fallo JUSTO DESPUÉS de sacar el dinero.
        // Gracias a @Transactional, el débito de arriba se deshace (rollback).
        if (simularFallo) {
            throw new IllegalStateException("Fallo simulado después del débito");
        }

        destino.acreditar(importe);
        movimientoRepository.save(new Movimiento(destino, TipoMovimiento.ABONO, importe,
                "Transferencia de " + origen.getIban()));

        Transferencia transferencia = transferenciaRepository.save(
                new Transferencia(origen, destino, importe, request.concepto()));

        // Fíjate: NO llamamos a cuentaRepository.save(origen). Hibernate detecta solo que
        // el saldo ha cambiado ("dirty checking") y hace el UPDATE al confirmar la transacción.
        return TransferenciaResponse.from(transferencia);
    }

    private Cuenta cargar(Long id, ModoBloqueo modo) {
        Optional<Cuenta> cuenta = (modo == ModoBloqueo.PESIMISTA)
                ? cuentaRepository.findByIdForUpdate(id)   // SELECT ... FOR UPDATE
                : cuentaRepository.findById(id);           // SELECT normal; @Version vigila al guardar
        return cuenta.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }
}
