package com.minibanca.core.service;

import com.minibanca.core.api.dto.MovimientoResponse;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.MovimientoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final CuentaRepository cuentaRepository;

    public MovimientoService(MovimientoRepository movimientoRepository, CuentaRepository cuentaRepository) {
        this.movimientoRepository = movimientoRepository;
        this.cuentaRepository = cuentaRepository;
    }

    /** Histórico de una cuenta (RF-4.1), comprobando antes que la cuenta es del usuario. */
    public Page<MovimientoResponse> historico(Long usuarioId, Long cuentaId, Pageable pageable) {
        if (!cuentaRepository.existsByIdAndUsuarioId(cuentaId, usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada");
        }
        return movimientoRepository.findByCuentaId(cuentaId, pageable)
                .map(MovimientoResponse::from);
    }
}
