package com.mini_banca.orquestador.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * La CLAVE con la que firmamos los JWT. Con ella:
 *  - el JwtEncoder FIRMA los tokens en el login,
 *  - el JwtDecoder COMPRUEBA en cada petición que el token lo firmamos nosotros y no ha caducado.
 * Si alguien cambia una sola letra del token (por ejemplo "rol": "ADMIN"), la firma deja de cuadrar.
 */
@Configuration
public class JwtConfig {

    @Bean
    SecretKey claveJwt(@Value("${jwt.secret}") String secreto) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable JWT_SECRET. Añádela al fichero .env (mira las instrucciones de la lección 7).");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secreto.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET debe estar en Base64", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET es demasiado corta: mínimo 32 bytes (256 bits) para HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey claveJwt, @Value("${jwt.emisor}") String emisor) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Además de la firma y la caducidad, exigimos que lo haya emitido "minibanca"
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(emisor));
        return decoder;
    }
}
