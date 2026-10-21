package com.minibanca.core.service;

import com.minibanca.core.api.dto.CrearCuentaRequest;
import com.minibanca.core.api.dto.CuentaConMovimientosResponse;
import com.minibanca.core.api.dto.CuentaResponse;
import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.Usuario;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Capa de SERVICIO: reglas de negocio y transacciones.
 * readOnly = true por defecto (consultas más ligeras); los métodos que escriben lo sobrescriben.
 */
@Service
@Transactional(readOnly = true)
public class CuentaService {

    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final GeneradorIban generadorIban;

    // Inyección por constructor: Spring nos pasa las dependencias, nosotros no hacemos "new"
    public CuentaService(CuentaRepository cuentaRepository,
                         UsuarioRepository usuarioRepository,
                         GeneradorIban generadorIban) {
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.generadorIban = generadorIban;
    }

    @Transactional
    public CuentaResponse crear(Long usuarioId, CrearCuentaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Cuenta cuenta = new Cuenta(usuario, generadorIban.generar(), request.alias(), request.divisa());
        return CuentaResponse.from(cuentaRepository.save(cuenta));
    }

    public Page<CuentaResponse> listar(Long usuarioId, Pageable pageable) {
        return cuentaRepository.findByUsuarioId(usuarioId, pageable)
                .map(CuentaResponse::from);
    }

    public CuentaResponse detalle(Long usuarioId, Long cuentaId) {
        // Si la cuenta existe pero es de otro, respondemos 404 igual: no revelamos que existe (RF-1.5)
        return cuentaRepository.findByIdAndUsuarioId(cuentaId, usuarioId)
                .map(CuentaResponse::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"));
    }

    /**
     * Lección 3: listado de cuentas CON sus movimientos.
     * optimizado = false -> provoca el N+1 a propósito (1 consulta + 1 por cada cuenta)
     * optimizado = true  -> @EntityGraph: todo en 1 consulta
     */
    public List<CuentaConMovimientosResponse> resumen(Long usuarioId, boolean optimizado) {
        List<Cuenta> cuentas = optimizado
                ? cuentaRepository.findConMovimientosByUsuarioId(usuarioId)
                : cuentaRepository.findByUsuarioIdOrderById(usuarioId);

        return cuentas.stream()
                .map(CuentaConMovimientosResponse::from)
                .toList();
    }
}
