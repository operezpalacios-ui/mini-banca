package com.minibanca.core.service;

import com.minibanca.core.api.dto.CrearCuentaRequest;
import com.minibanca.core.api.dto.CuentaConMovimientosResponse;
import com.minibanca.core.api.dto.CuentaLigeraResponse;
import com.minibanca.core.api.dto.CuentaResponse;
import com.minibanca.core.api.dto.ResumenMensualResponse;
import com.minibanca.core.config.CacheConfig;
import com.minibanca.core.domain.AccionAuditoria;
import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.EstadoCuenta;
import com.minibanca.core.domain.Usuario;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.error.ReglaNegocioException;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.UsuarioRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
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
    private final AuditoriaService auditoria;
    private final CacheCuentas cache;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // Inyección por constructor: Spring nos pasa las dependencias, nosotros no hacemos "new"
    public CuentaService(CuentaRepository cuentaRepository,
                         UsuarioRepository usuarioRepository,
                         GeneradorIban generadorIban,
                         AuditoriaService auditoria,
                         CacheCuentas cache) {
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.generadorIban = generadorIban;
        this.auditoria = auditoria;
        this.cache = cache;
    }

    @Transactional
    public CuentaResponse crear(Long usuarioId, CrearCuentaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Cuenta cuenta = new Cuenta(usuario, generadorIban.generar(), request.alias(), request.divisa());
        CuentaResponse creada = CuentaResponse.from(cuentaRepository.save(cuenta));
        cache.olvidarTrasCommit(usuarioId);   // lección 11: su lista de cuentas ha cambiado
        return creada;
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

    // ===================== Lección 9 =====================

    /**
     * RF-2.3: cerrar una cuenta.
     * La cuenta se lee CON CERROJO (FOR UPDATE): si en ese mismo instante está entrando una
     * transferencia, esperamos a que termine y vemos el saldo ya actualizado. Sin cerrojo
     * podríamos comprobar "saldo 0", y un milisegundo después entrar dinero en una cuenta cerrada.
     */
    @Transactional
    public CuentaResponse cerrar(Long usuarioId, Long cuentaId) {
        if (!cuentaRepository.existsByIdAndUsuarioId(cuentaId, usuarioId)) {
            throw new RecursoNoEncontradoException("Cuenta no encontrada");
        }
        Cuenta cuenta = cuentaRepository.findByIdForUpdate(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"));

        cuenta.cerrar();   // las reglas (saldo 0, no cerrada ya) están en la entidad
        auditoria.registrar(usuarioId, AccionAuditoria.CUENTA_CERRADA, "Cuenta " + cuenta.getIban() + " cerrada");
        cache.olvidarTrasCommit(usuarioId);
        // No hace falta save(): la entidad está "gestionada" y Hibernate guarda el cambio al hacer commit
        return CuentaResponse.from(cuenta);
    }

    /**
     * RF-7.6: proyección. Solo las cuentas ACTIVAS (las que sirven como origen de una transferencia).
     *
     * Lección 11: @Cacheable = "antes de ejecutar el método, mira en la caché cuentasLigeras con la
     * clave usuarioId. Si está, devuélvelo SIN ejecutar el método. Si no, ejecútalo y apunta el resultado".
     * ⚠️ Esto es solo para ENSEÑAR. Para MOVER dinero nunca se lee la caché: transferir() lee la BBDD con cerrojo.
     */
    @Cacheable(cacheNames = CacheConfig.CUENTAS_LIGERAS, key = "#usuarioId")
    public List<CuentaLigeraResponse> ligeras(Long usuarioId) {
        return cuentaRepository.findLigerasByUsuarioIdAndEstadoOrderByIdAsc(usuarioId, EstadoCuenta.ACTIVA)
                .stream().map(CuentaLigeraResponse::from).toList();
    }

    /**
     * RF-7.6: consulta nativa. Ingresos y gastos por mes de los últimos N meses (incluido el actual).
     *
     * Lección 11: ⚠️ la clave INCLUYE el usuarioId. Si fuera solo "cuenta:meses", otro usuario que
     * pidiera la cuenta 1 recibiría la chuleta SIN pasar por la comprobación de propietario de abajo:
     * ¡vería datos ajenos! Con el usuario en la clave, no encuentra nada, ejecuta el método y da 404.
     */
    @Cacheable(cacheNames = CacheConfig.RESUMEN_MENSUAL, key = "#usuarioId + ':' + #cuentaId + ':' + #meses")
    public List<ResumenMensualResponse> resumenMensual(Long usuarioId, Long cuentaId, int meses) {
        if (meses < 1 || meses > 36) {
            throw new ReglaNegocioException("El parámetro meses debe estar entre 1 y 36");
        }
        if (!cuentaRepository.existsByIdAndUsuarioId(cuentaId, usuarioId)) {
            throw new RecursoNoEncontradoException("Cuenta no encontrada");
        }
        var desde = LocalDate.now(MADRID).withDayOfMonth(1).minusMonths(meses - 1L)
                .atStartOfDay(MADRID).toInstant();
        return cuentaRepository.resumenMensual(cuentaId, desde)
                .stream().map(ResumenMensualResponse::from).toList();
    }
}
