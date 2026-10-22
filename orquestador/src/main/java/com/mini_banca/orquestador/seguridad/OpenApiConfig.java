package com.mini_banca.orquestador.seguridad;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Lección 9 (RF-9.1): OpenAPI / Swagger.
 *
 * springdoc LEE nuestros controllers (rutas, parámetros, records) y genera solo:
 *   - /v3/api-docs     -> la descripción de la API en JSON (el "contrato" OpenAPI)
 *   - /swagger-ui.html -> una web para probarla desde el navegador
 *
 * @SecurityScheme declara "esta API usa Authorization: Bearer <jwt>": en Swagger aparece
 * el botón "Authorize" para pegar el token una vez y que se mande en todas las pruebas.
 *
 * Solo documentamos el ORQUESTADOR: el core es interno y nadie de fuera debe conocerlo.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Mini banca API", version = "v1",
                description = "API pública (BFF) de Mini banca. Haz login, copia el accessToken y pulsa Authorize."),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
