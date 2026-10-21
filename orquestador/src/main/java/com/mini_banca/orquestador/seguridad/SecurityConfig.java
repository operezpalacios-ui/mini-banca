package com.mini_banca.orquestador.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Las "normas del portero" (RF-1.4, RF-1.5).
 * Spring Security es una cadena de filtros que se ejecuta ANTES de cada controller.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, ProblemaSeguridadHandler problemas) throws Exception {
        http
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
