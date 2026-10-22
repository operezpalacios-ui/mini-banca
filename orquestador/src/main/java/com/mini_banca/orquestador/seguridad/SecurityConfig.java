package com.mini_banca.orquestador.seguridad;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Las "normas del portero" (RF-1.4, RF-1.5).
 * Spring Security es una cadena de filtros que se ejecuta ANTES de cada controller.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, ProblemaSeguridadHandler problemas) throws Exception {
        http
            // Lección 10: CORS. Va DENTRO de Spring Security para que la "pregunta previa" del
            // navegador (OPTIONS, sin token) se conteste antes de exigir el token.
            .cors(cors -> cors.configurationSource(cors()))
            // CSRF protege formularios con cookies de sesión. Nosotros no usamos cookies sino un
            // token en la cabecera Authorization, así que no aplica.
            .csrf(csrf -> csrf.disable())
            // STATELESS: el servidor no guarda sesiones. Cada petición trae su token y se valida sola.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reglas -> reglas
                    // Público: registrarse, entrar y el health check
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login",
                            "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                    // Lección 9: Swagger (la documentación es pública; los endpoints siguen pidiendo token).
                    // En producción se apaga entero con SWAGGER_ENABLED=false.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // Lección 9: la descarga CSV termina en un segundo "pase" interno (dispatch ASYNC)
                    // de la MISMA petición, que ya fue autorizada en el primer pase. Sin esto, ese
                    // segundo pase podría acabar en un 401/403 a mitad de la descarga.
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    // Solo administradores (403 si eres USER)
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    // Todo lo demás: hace falta un token válido (401 si no)
                    .anyRequest().authenticated())
            // "Servidor de recursos": acepta peticiones con  Authorization: Bearer <jwt>
            .oauth2ResourceServer(oauth -> oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesDesdeElToken()))
                    .authenticationEntryPoint(problemas))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(problemas)
                    .accessDeniedHandler(problemas));
        return http.build();
    }

    @Value("${cors.origenes-permitidos}")
    private List<String> origenesPermitidos;

    /**
     * Lección 10: CORS = la lista de WEBS que pueden llamar a esta API desde un navegador.
     *
     * El navegador, por seguridad, NO deja que una página de http://localhost:4200 (Angular)
     * lea respuestas de http://localhost:8081 (otro "origen": otro puerto ya cuenta) salvo que
     * el servidor diga "a esa web sí la dejo". Antes de un POST con JSON o con Authorization,
     * el navegador manda una pregunta previa (OPTIONS, "preflight") y solo sigue si le dejamos.
     *
     * OJO: CORS protege a los USUARIOS de webs maliciosas, NO protege la API (curl o IntelliJ
     * lo ignoran). La API la sigue protegiendo el JWT.
     */
    private CorsConfigurationSource cors() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origenesPermitidos);                 // nunca "*" en una API con login
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Trace-Id"));
        // Cabeceras de la RESPUESTA que Angular podrá leer (por defecto el navegador las esconde)
        c.setExposedHeaders(List.of("Idempotent-Replayed", "Content-Disposition", "X-Trace-Id"));
        c.setAllowCredentials(false);   // usamos token en cabecera, no cookies
        c.setMaxAge(Duration.ofHours(1));   // el navegador recuerda la respuesta del OPTIONS 1 hora
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }

    /** Convierte el claim "rol": "ADMIN" del token en el permiso ROLE_ADMIN que entiende hasRole("ADMIN"). */
    private JwtAuthenticationConverter rolesDesdeElToken() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("rol");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
