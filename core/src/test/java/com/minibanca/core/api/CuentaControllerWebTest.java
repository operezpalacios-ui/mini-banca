package com.minibanca.core.api;

import com.minibanca.core.api.dto.CuentaResponse;
import com.minibanca.core.domain.EstadoCuenta;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.service.CuentaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST DE LA CAPA WEB (@WebMvcTest): arranca SOLO la parte HTTP de Spring (controller, filtros,
 * GlobalExceptionHandler, validación, JSON). El servicio es un mock: aquí no hay base de datos.
 *
 * MockMvc simula peticiones HTTP sin abrir ningún puerto: perform(get(...)) -> andExpect(...).
 * Comprobamos el CONTRATO de la API: códigos de estado, formato ProblemDetail, validaciones...
 */
@WebMvcTest(CuentaController.class)
class CuentaControllerWebTest {

    private static final String CLAVE = "clave-interna-solo-para-tests-0123456789";   // la de test/resources

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CuentaService cuentaService;

    @Test
    void sinClaveInternaResponde401YNiSiquieraLlegaAlServicio() throws Exception {
        mvc.perform(get("/internal/v1/cuentas/1").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.traceId").exists());

        verifyNoInteractions(cuentaService);
    }

    @Test
    void cuentaInexistenteDevuelve404EnFormatoProblemDetail() throws Exception {
        when(cuentaService.detalle(1L, 99L)).thenThrow(new RecursoNoEncontradoException("Cuenta no encontrada"));

        mvc.perform(get("/internal/v1/cuentas/99")
                        .header("X-Internal-Key", CLAVE)
                        .header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Cuenta no encontrada"))
                .andExpect(jsonPath("$.instance").value("/internal/v1/cuentas/99"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void detalleCorrecto() throws Exception {
        when(cuentaService.detalle(1L, 7L)).thenReturn(new CuentaResponse(7L, "ES0000000000000000000007", "Ahorro",
                "EUR", new BigDecimal("12.34"), EstadoCuenta.ACTIVA, Instant.now(), null));

        mvc.perform(get("/internal/v1/cuentas/7")
                        .header("X-Internal-Key", CLAVE)
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value("ES0000000000000000000007"))
                .andExpect(jsonPath("$.saldo").value(12.34));
    }

    @Test
    void divisaNoValidaDevuelve400ConElCampoQueFalla() throws Exception {
        mvc.perform(post("/internal/v1/cuentas")
                        .header("X-Internal-Key", CLAVE)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "alias": "Viajes", "divisa": "euros" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("divisa"));

        verifyNoInteractions(cuentaService);
    }
}
