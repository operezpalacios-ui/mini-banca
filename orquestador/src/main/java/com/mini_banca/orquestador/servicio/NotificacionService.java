package com.mini_banca.orquestador.servicio;

import com.mini_banca.orquestador.api.dto.TransferenciaDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Notificación SIMULADA (RF-0.5, paso 3). En la vida real enviaría un email, un SMS o un push.
 * Aquí solo escribe en el log, y puede fallar a propósito para practicar.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    public void transferenciaRealizada(Long usuarioId, TransferenciaDto t, boolean simularFallo) {
        if (simularFallo) {
            throw new IllegalStateException("El servidor de notificaciones no responde (simulado)");
        }
        log.info("📧 NOTIFICACIÓN a usuario {}: has enviado {} € a {} (transferencia {})",
                usuarioId, t.importe(), t.ibanDestino(), t.id());
    }
}
