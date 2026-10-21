package com.minibanca.core.service;

import com.minibanca.core.api.dto.RefreshTokenResponse;
import com.minibanca.core.api.dto.RotacionResponse;
import com.minibanca.core.api.dto.UsuarioResponse;
import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.domain.RefreshToken;
import com.minibanca.core.domain.Usuario;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.error.SesionInvalidaException;
import com.minibanca.core.error.UsuarioDesactivadoException;
import com.minibanca.core.repository.RefreshTokenRepository;
import com.minibanca.core.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * REFRESH TOKENS (RF-1.3).
 *
 * El access token (JWT) dura 15 min y NO se puede anular: es una firma, no está en ninguna tabla.
 * El refresh token dura 7 días y SÍ está en la BBDD, así que se puede revocar (logout, bloqueo...).
 * El front usa el refresh para pedir un JWT nuevo cuando el viejo caduca, sin volver a pedir contraseña.
 *
 * ROTACIÓN: cada refresh token sirve UNA sola vez. Al usarlo se revoca y se entrega uno nuevo.
 * Si alguien usa uno YA gastado, es que hay dos copias (¡posible robo!): revocamos TODAS las sesiones.
 */
@Service
public class RefreshTokenService {

    private static final Duration DURACION = Duration.ofDays(7);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoria;

    public RefreshTokenService(RefreshTokenRepository repo, UsuarioRepository usuarioRepository,
                               AuditoriaService auditoria) {
        this.repo = repo;
        this.usuarioRepository = usuarioRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public RefreshTokenResponse crear(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return emitir(usuario);
    }

    /**
     * noRollbackFor: en el caso de REUTILIZACIÓN revocamos todas las sesiones y LUEGO lanzamos
     * la excepción. Sin noRollbackFor, el rollback desharía la revocación (¡lección 7 otra vez!).
     */
    @Transactional(noRollbackFor = SesionInvalidaException.class)
    public RotacionResponse rotar(String tokenEnClaro) {
        Instant ahora = Instant.now();
        RefreshToken actual = repo.findByTokenHash(hash(tokenEnClaro))
                .orElseThrow(SesionInvalidaException::new);
        Usuario usuario = actual.getUsuario();

        if (actual.isRevocado()) {
            repo.revocarTodos(usuario.getId());
            auditoria.registrar(usuario.getId(), AccionAuditoria.REFRESH_REUTILIZADO,
                    "Se reutilizó un refresh token ya gastado: se cierran todas las sesiones");
            throw new SesionInvalidaException();
        }
        if (!actual.estaVigente(ahora)) {
            throw new SesionInvalidaException();
        }
        if (!usuario.isActivo()) {
            throw new UsuarioDesactivadoException();
        }

        actual.revocar();                                   // este ya no vale más
        RefreshTokenResponse nuevo = emitir(usuario);       // y entregamos uno nuevo
        return new RotacionResponse(UsuarioResponse.from(usuario), nuevo.refreshToken(), nuevo.expiraEn());
    }

    /** Logout: revoca el refresh token. Si no existe o ya estaba revocado, no pasa nada (idempotente). */
    @Transactional
    public void revocar(String tokenEnClaro) {
        repo.findByTokenHash(hash(tokenEnClaro)).ifPresent(token -> {
            if (!token.isRevocado()) {
                token.revocar();
                auditoria.registrar(token.getUsuario().getId(), AccionAuditoria.LOGOUT, null);
            }
        });
    }

    @Transactional
    public void revocarTodos(Long usuarioId) {
        repo.revocarTodos(usuarioId);
    }

    private RefreshTokenResponse emitir(Usuario usuario) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = Instant.now().plus(DURACION);
        repo.save(new RefreshToken(usuario, hash(token), expira));   // guardamos el HASH, no el token
        return new RefreshTokenResponse(token, expira);
    }

    /**
     * SHA-256 y no BCrypt: el token ya es aleatorio y largo (256 bits), así que no hace falta
     * un hash "lento". Y SHA-256 nos permite BUSCARLO en la BBDD (con BCrypt no se podría).
     */
    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
