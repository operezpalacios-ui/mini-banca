package com.mini_banca.orquestador.servicio;

import com.mini_banca.orquestador.api.dto.TransferenciaDto;
import com.mini_banca.orquestador.api.dto.TransferenciaRequest;
import com.mini_banca.orquestador.cliente.CoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * ORQUESTACIÓN DE LA TRANSFERENCIA: una "saga simple" (RF-0.5).
 *
 * Una saga es una operación de varios pasos repartida entre servicios. Aquí NO hay una transacción
 * que lo envuelva todo (no existen las "transacciones distribuidas" en este proyecto).
 * La regla de oro: la ATOMICIDAD del dinero vive dentro del core (paso 2). Lo demás se coordina:
 *
 *   1. Orquestador: ¿cabe en el límite diario?            -> si no, 422 y no se hace nada
 *   2. Core: transferencia en UNA transacción ACID         -> si falla, no se audita ni se notifica
 *   3. Orquestador: auditoría + notificación               -> si FALLAN, la transferencia NO se deshace
 *                                                             (el dinero ya se movió bien); se registra el fallo
 */
@Service
public class TransferenciaSagaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaSagaService.class);

    private final CoreClient core;
    private final NotificacionService notificaciones;
    private final BigDecimal limiteDiario;

    public TransferenciaSagaService(CoreClient core, NotificacionService notificaciones,
                                    @Value("${transferencias.limite-diario}") BigDecimal limiteDiario) {
        this.core = core;
        this.notificaciones = notificaciones;
        this.limiteDiario = limiteDiario;
    }

    public ResponseEntity<TransferenciaDto> transferir(Long usuarioId, String idempotencyKey,
                                                       TransferenciaRequest request, boolean simularFalloNotificacion) {
        // ---- PASO 1: límite diario ----
        // ⚠️ Ojo: esta comprobación NO es atómica. Dos transferencias simultáneas podrían pasarla a la vez.
        // Para garantizarlo al 100 % habría que comprobarlo dentro de la transacción del core (con el cerrojo).
        BigDecimal yaTransferido = core.totalTransferidoHoy(usuarioId);
        if (yaTransferido.add(request.importe()).compareTo(limiteDiario) > 0) {
            throw new LimiteDiarioExcedidoException(limiteDiario, yaTransferido);
        }

        // ---- PASO 2: el core mueve el dinero (todo o nada). Si falla, la excepción sube y la saga para aquí.
        ResponseEntity<TransferenciaDto> respuesta = core.transferir(usuarioId, idempotencyKey, request);
        TransferenciaDto t = respuesta.getBody();

        boolean repetida = "true".equals(respuesta.getHeaders().getFirst("Idempotent-Replayed"));
        if (repetida || t == null) {
            return respuesta;   // ya se auditó y notificó la primera vez
        }

        // ---- PASO 3a: auditoría. Si falla, lo dejamos en el log, pero NO deshacemos la transferencia.
        try {
            core.auditar(usuarioId, "TRANSFERENCIA",
                    "Transferencia " + t.id() + ": " + t.importe() + " € de " + t.ibanOrigen() + " a " + t.ibanDestino());
        } catch (Exception e) {
            log.error("No se pudo auditar la transferencia {} (la transferencia SÍ está hecha)", t.id(), e);
        }

        // ---- PASO 3b: notificación. Si falla, se REGISTRA para reintentarla más tarde.
        try {
            notificaciones.transferenciaRealizada(usuarioId, t, simularFalloNotificacion);
        } catch (Exception e) {
            log.warn("Notificación de la transferencia {} fallida: {}. Queda registrada para reintentar.",
                    t.id(), e.getMessage());
            try {
                core.auditar(usuarioId, "NOTIFICACION_FALLIDA", "Transferencia " + t.id() + ": " + e.getMessage());
            } catch (Exception ignorada) {
                log.error("Tampoco se pudo registrar el fallo de notificación de la transferencia {}", t.id());
            }
        }

        return respuesta;
    }
}
