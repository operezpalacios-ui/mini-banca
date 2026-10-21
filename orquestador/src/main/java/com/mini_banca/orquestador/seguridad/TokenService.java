package com.mini_banca.orquestador.seguridad;

import com.mini_banca.orquestador.api.dto.UsuarioDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Fabrica los JWT ("pases") que entregamos en el login. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final String emisor;
    private final Duration duracion;

    public TokenService(JwtEncoder encoder,
                        @Value("${jwt.emisor}") String emisor,
                        @Value("${jwt.duracion}") Duration duracion) {
        this.encoder = encoder;
        this.emisor = emisor;
        this.duracion = duracion;
    }

    /**
     * Un JWT son 3 partes separadas por puntos:  CABECERA . DATOS . FIRMA
     * Los DATOS ("claims") van en Base64, NO cifrados: cualquiera puede leerlos (prueba en jwt.io).
     * Por eso nunca se mete nada secreto dentro. Lo que lo hace seguro es la FIRMA.
     */
    public String emitir(UsuarioDto usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)                                // quién lo emite
                .subject(String.valueOf(usuario.id()))         // de quién es (el id del usuario)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))               // caduca en 15 minutos
                .claim("rol", usuario.rol())                   // USER o ADMIN
                .claim("nombre", usuario.nombre())
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    public long segundosDeValidez() {
        return duracion.toSeconds();
    }
}
