package com.mini_banca.orquestador.servicio;

import com.mini_banca.orquestador.api.dto.TransferenciaDto;
import com.mini_banca.orquestador.api.dto.TransferenciaRequest;
import com.mini_banca.orquestador.cliente.CoreClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La saga de la lección 8, probada paso a paso con mocks (sin Spring, sin core real). */
@ExtendWith(MockitoExtension.class)
class TransferenciaSagaServiceTest {

    @Mock CoreClient core;
    @Mock NotificacionService notificaciones;

    private TransferenciaSagaService saga;
    private SimpleMeterRegistry metricas;   // lección 13: un registro de métricas en memoria, para el test

    private final TransferenciaRequest peticion =
            new TransferenciaRequest(1L, "ES2222222222222222222222", new BigDecimal("100.00"), "Alquiler");

    @BeforeEach
    void preparar() {
        metricas = new SimpleMeterRegistry();
        saga = new TransferenciaSagaService(core, notificaciones, new BigDecimal("1000.00"), metricas);
    }

    @Test
    void siSuperaElLimiteDiarioNiSiquieraSeLlamaAlCore() {
        when(core.totalTransferidoHoy(7L)).thenReturn(new BigDecimal("950.00"));   // 950 + 100 > 1000

        assertThatThrownBy(() -> saga.transferir(7L, "clave-12345678", peticion, false))
                .isInstanceOf(LimiteDiarioExcedidoException.class);

        verify(core, never()).transferir(anyLong(), anyString(), any());
        // Lección 13: y queda contado en la métrica con resultado=limite_diario
        assertThat(metricas.get(TransferenciaSagaService.METRICA).tag("resultado", "limite_diario").timer().count())
                .isEqualTo(1);
    }

    @Test
    void siFallaLaNotificacionLaTransferenciaSeMantieneYSeAuditaElFallo() {
        when(core.totalTransferidoHoy(7L)).thenReturn(BigDecimal.ZERO);
        when(core.transferir(7L, "clave-12345678", peticion))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(transferenciaHecha()));
        doThrow(new IllegalStateException("Servidor de correo caído"))
                .when(notificaciones).transferenciaRealizada(eq(7L), any(), anyBoolean());

        ResponseEntity<TransferenciaDto> respuesta = saga.transferir(7L, "clave-12345678", peticion, false);

        // El dinero YA se movió en el core: la saga NO lo deshace por un fallo de notificación
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(core).auditar(eq(7L), eq("TRANSFERENCIA"), anyString());
        verify(core).auditar(eq(7L), eq("NOTIFICACION_FALLIDA"), anyString());
        assertThat(metricas.get(TransferenciaSagaService.METRICA).tag("resultado", "ok").timer().count())
                .isEqualTo(1);
    }

    @Test
    void unaPeticionRepetidaNoSeVuelveANotificarNiAuditar() {
        when(core.totalTransferidoHoy(7L)).thenReturn(BigDecimal.ZERO);
        when(core.transferir(7L, "clave-12345678", peticion))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .header("Idempotent-Replayed", "true")
                        .body(transferenciaHecha()));

        saga.transferir(7L, "clave-12345678", peticion, false);

        verify(notificaciones, never()).transferenciaRealizada(anyLong(), any(), anyBoolean());
        verify(core, never()).auditar(anyLong(), anyString(), anyString());
    }

    private static TransferenciaDto transferenciaHecha() {
        return new TransferenciaDto(55L, 1L, "ES1111111111111111111111", "ES2222222222222222222222",
                new BigDecimal("100.00"), "Alquiler", Instant.now(), "COMPLETADA", null, null);
    }
}
