package com.mini_banca.orquestador.seguridad;

import com.mini_banca.orquestador.api.dto.Pagina;
import com.mini_banca.orquestador.api.dto.UsuarioDto;
import com.mini_banca.orquestador.cliente.CoreClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las reglas de seguridad del orquestador, comprobadas AUTOMÁTICAMENTE (lecciones 7, 8 y 10).
 *
 * @SpringBootTest + @AutoConfigureMockMvc: arranca el orquestador entero (con Spring Security,
 * CORS, JWT...) pero sin abrir puertos. El core es un mock: aquí solo probamos la "puerta".
 * Los tokens son JWT REALES firmados con la clave de test (test/resources/application.properties).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadTest {

    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;

    @MockitoBean CoreClient core;

    private String tokenDe(long id, String rol) {
        return "Bearer " + tokens.emitir(new UsuarioDto(id, "u" + id + "@test.com", "Usuario " + id, rol, true));
    }

    @Test
    void sinTokenDa401EnFormatoProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/cuentas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"));

        verifyNoInteractions(core);
    }

    @Test
    void tokenFalsificadoDa401() throws Exception {
        String bueno = tokenDe(1, "USER");
        String trucado = bueno.substring(0, bueno.length() - 4) + "AAAA";   // cambiamos la firma

        mvc.perform(get("/api/v1/cuentas").header("Authorization", trucado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conTokenValidoLlegaAlCoreConElIdDelToken() throws Exception {
        when(core.listarCuentas(eq(1L), anyInt(), anyInt(), isNull()))
                .thenReturn(new Pagina<>(List.of(), new Pagina.Metadatos(10, 0, 0, 0)));

        mvc.perform(get("/api/v1/cuentas").header("Authorization", tokenDe(1, "USER")))
                .andExpect(status().isOk());

        // El id que se manda al core sale del TOKEN, no de nada que el navegador pueda inventarse
        verify(core).listarCuentas(eq(1L), anyInt(), anyInt(), isNull());
    }

    @Test
    void unUsuarioNormalNoEntraEnAdmin403() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", tokenDe(1, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permiso"));

        verifyNoInteractions(core);
    }

    @Test
    void unAdminSiEntraEnAdmin() throws Exception {
        when(core.listarUsuarios(eq(99L), anyInt(), anyInt()))
                .thenReturn(new Pagina<>(List.of(), new Pagina.Metadatos(20, 0, 0, 0)));

        mvc.perform(get("/api/admin/usuarios").header("Authorization", tokenDe(99, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void corsPermiteAlFrontendDeAngular() throws Exception {
        mvc.perform(options("/api/v1/cuentas")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void corsRechazaAOtraWeb() throws Exception {
        mvc.perform(options("/api/v1/cuentas")
                        .header("Origin", "https://web-malvada.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void laDocumentacionOpenApiEsPublica() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Mini banca API"));
    }
}
