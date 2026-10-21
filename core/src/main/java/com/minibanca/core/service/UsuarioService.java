package com.minibanca.core.service;

import com.minibanca.core.api.dto.RegistroUsuarioRequest;
import com.minibanca.core.api.dto.UsuarioResponse;
import com.minibanca.core.api.dto.VerificarCredencialesRequest;
import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.domain.Rol;
import com.minibanca.core.domain.Usuario;
import com.minibanca.core.error.ConflictoException;
import com.minibanca.core.error.CredencialesInvalidasException;
import com.minibanca.core.error.CuentaBloqueadaException;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.error.UsuarioDesactivadoException;
import com.minibanca.core.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private static final int MAX_INTENTOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final RefreshTokenService refreshTokens;
    private final String hashFalso;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoria, RefreshTokenService refreshTokens) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.refreshTokens = refreshTokens;
        // Un hash de mentira para cuando el email no existe (ver verificar())
        this.hashFalso = passwordEncoder.encode("contraseña-que-nunca-coincide");
    }

    /** RF-1.1: registro con email único y contraseña guardada con BCrypt. */
    @Transactional
    public UsuarioResponse registrar(RegistroUsuarioRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictoException("Ya existe un usuario con ese email");
        }
        String hash = passwordEncoder.encode(request.password());   // NUNCA guardamos la contraseña en claro
        Usuario usuario = usuarioRepository.save(new Usuario(email, hash, request.nombre().trim(), Rol.USER));
        return UsuarioResponse.from(usuario);
    }

    /**
     * RF-1.2: comprueba email + contraseña. Lo llama el orquestador en el login.
     *
     * ⚠️ noRollbackFor: si la contraseña es incorrecta lanzamos CredencialesInvalidasException...
     * pero QUEREMOS guardar el intento fallido. Sin noRollbackFor, @Transactional haría rollback
     * y el contador nunca subiría: el bloqueo tras 5 intentos no funcionaría jamás.
     */
    @Transactional(noRollbackFor = {CredencialesInvalidasException.class, CuentaBloqueadaException.class,
            UsuarioDesactivadoException.class})
    public UsuarioResponse verificar(VerificarCredencialesRequest request) {
        Instant ahora = Instant.now();
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);

        if (usuario == null) {
            // Comparamos igualmente contra un hash falso para tardar LO MISMO que con un email real.
            // Si respondiéramos más rápido, un atacante sabría qué emails existen midiendo el tiempo.
            passwordEncoder.matches(request.password(), hashFalso);
            auditoria.registrar(null, AccionAuditoria.LOGIN_FALLIDO, "Email inexistente");
            throw new CredencialesInvalidasException();
        }
        if (!usuario.isActivo()) {
            auditoria.registrar(usuario.getId(), AccionAuditoria.LOGIN_FALLIDO, "Cuenta desactivada");
            throw new UsuarioDesactivadoException();
        }
        if (usuario.estaBloqueado(ahora)) {
            auditoria.registrar(usuario.getId(), AccionAuditoria.LOGIN_FALLIDO, "Cuenta bloqueada temporalmente");
            throw new CuentaBloqueadaException(usuario.getBloqueadoHasta());
        }
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            usuario.registrarIntentoFallido(ahora, MAX_INTENTOS, DURACION_BLOQUEO);   // se guarda gracias a noRollbackFor
            if (usuario.estaBloqueado(ahora)) {
                auditoria.registrar(usuario.getId(), AccionAuditoria.LOGIN_BLOQUEADO, "5 intentos fallidos");
                throw new CuentaBloqueadaException(usuario.getBloqueadoHasta());
            }
            auditoria.registrar(usuario.getId(), AccionAuditoria.LOGIN_FALLIDO, "Contraseña incorrecta");
            throw new CredencialesInvalidasException();
        }

        usuario.registrarAccesoCorrecto();
        auditoria.registrar(usuario.getId(), AccionAuditoria.LOGIN_OK, null);
        return UsuarioResponse.from(usuario);
    }

    /** RF-6.1: listado paginado de usuarios (solo lo pedirá el orquestador para un ADMIN). */
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findAll(pageable).map(UsuarioResponse::from);
    }

    /**
     * RF-6.2: un ADMIN bloquea o desbloquea a un usuario. Al bloquear, cerramos TODAS sus sesiones
     * (revocamos sus refresh tokens). Su JWT actual seguirá valiendo hasta que caduque (máx. 15 min):
     * por eso los access tokens deben durar poco.
     */
    @Transactional
    public UsuarioResponse cambiarBloqueo(Long adminId, Long usuarioId, boolean bloquear) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        if (bloquear) {
            usuario.desactivar();
            refreshTokens.revocarTodos(usuarioId);
            auditoria.registrar(adminId, AccionAuditoria.BLOQUEO_USUARIO, "Usuario bloqueado: " + usuarioId);
        } else {
            usuario.activar();
            auditoria.registrar(adminId, AccionAuditoria.DESBLOQUEO_USUARIO, "Usuario desbloqueado: " + usuarioId);
        }
        return UsuarioResponse.from(usuario);
    }
}
