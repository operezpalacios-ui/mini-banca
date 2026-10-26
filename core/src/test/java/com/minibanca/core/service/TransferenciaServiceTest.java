package com.minibanca.core.service;

import com.minibanca.core.api.dto.CrearTransferenciaRequest;
import com.minibanca.core.domain.Cuenta;
import com.minibanca.core.domain.Rol;
import com.minibanca.core.domain.Transferencia;
import com.minibanca.core.domain.Usuario;
import com.minibanca.core.error.ConflictoException;
import com.minibanca.core.error.RecursoNoEncontradoException;
import com.minibanca.core.error.ReglaNegocioException;
import com.minibanca.core.error.SaldoInsuficienteException;
import com.minibanca.core.repository.CuentaRepository;
import com.minibanca.core.repository.MovimientoRepository;
import com.minibanca.core.repository.TransferenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TEST UNITARIO CON MOCKS (Mockito).
 *
 * TransferenciaService necesita repositorios (BBDD), auditoría y caché. Para probar SOLO su lógica,
 * le damos "dobles" de mentira (mocks): objetos que hacen lo que les digamos en cada test
 *   when(repo.metodo(...)).thenReturn(...)     -> "si te preguntan esto, contesta esto"
 *   verify(repo).metodo(...)                   -> "comprueba que te llamaron así"
 * Sin base de datos: estos tests tardan milisegundos y no dependen de nada externo.
 *
 * (Lo que NO prueban: el SQL, los cerrojos reales ni la transacción. Para eso está el test de
 * integración con PostgreSQL de verdad: TransferenciaConcurrenciaIntegracionTest.)
 */
@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    private static final String IBAN_ORIGEN = "ES1111111111111111111111";
    private static final String IBAN_DESTINO = "ES2222222222222222222222";
    private static final String CLAVE = "clave-idempotencia-1";

    @Mock CuentaRepository cuentaRepository;
    @Mock MovimientoRepository movimientoRepository;
    @Mock TransferenciaRepository transferenciaRepository;
    @Mock AuditoriaService auditoria;
    @Mock CacheCuentas cache;

    // Crea un TransferenciaService "de verdad" y le inyecta los mocks de arriba por el constructor
    @InjectMocks TransferenciaService servicio;

    private Cuenta origen;    // de Ana (usuario 1), id 10, 100 €
    private Cuenta destino;   // de Luis (usuario 2), id 20, 0 €

    @BeforeEach
    void preparar() {
        Usuario ana = conId(new Usuario("ana@test.com", "h", "Ana", Rol.USER), 1L);
        Usuario luis = conId(new Usuario("luis@test.com", "h", "Luis", Rol.USER), 2L);
        origen = conId(new Cuenta(ana, IBAN_ORIGEN, "Nómina", "EUR"), 10L);
        destino = conId(new Cuenta(luis, IBAN_DESTINO, null, "EUR"), 20L);
        origen.acreditar(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Transferencia correcta: mueve el dinero, apunta 2 movimientos y limpia la caché de los 2 usuarios")
    void transferenciaCorrecta() {
        prepararCuentasEncontradas();
        when(transferenciaRepository.findByIdempotencyKey(CLAVE)).thenReturn(Optional.empty());
        when(transferenciaRepository.save(any(Transferencia.class))).then(returnsFirstArg());

        ResultadoTransferencia r = servicio.transferir(1L, CLAVE, peticion("30.00"), ModoBloqueo.PESIMISTA, false);

        assertThat(r.repetida()).isFalse();
        assertThat(origen.getSaldo()).isEqualByComparingTo("70.00");
        assertThat(destino.getSaldo()).isEqualByComparingTo("30.00");
        verify(movimientoRepository, times(2)).save(any());   // un CARGO y un ABONO
        verify(cache).olvidarTrasCommit(1L, 2L);
    }

    @Test
    @DisplayName("Si la cuenta origen no es del usuario: 404 y no se toca nada")
    void origenAjena() {
        when(cuentaRepository.existsByIdAndUsuarioId(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.transferir(1L, CLAVE, peticion("30.00"), ModoBloqueo.PESIMISTA, false))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(movimientoRepository, transferenciaRepository, cache);
        assertThat(origen.getSaldo()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Origen y destino iguales: regla de negocio (422)")
    void mismaCuenta() {
        when(cuentaRepository.existsByIdAndUsuarioId(10L, 1L)).thenReturn(true);
        when(cuentaRepository.findIdByIban(IBAN_DESTINO)).thenReturn(Optional.of(10L));   // ¡es la misma!

        assertThatThrownBy(() -> servicio.transferir(1L, CLAVE, peticion("30.00"), ModoBloqueo.PESIMISTA, false))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("distintas");

        verify(cuentaRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Sin saldo suficiente: SaldoInsuficiente y ningún movimiento")
    void sinSaldo() {
        prepararCuentasEncontradas();
        when(transferenciaRepository.findByIdempotencyKey(CLAVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.transferir(1L, CLAVE, peticion("500.00"), ModoBloqueo.PESIMISTA, false))
                .isInstanceOf(SaldoInsuficienteException.class);

        verify(movimientoRepository, never()).save(any());
        verify(transferenciaRepository, never()).save(any());
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("IDEMPOTENCIA: misma clave y mismos datos -> devuelve la original y NO mueve dinero")
    void repeticionIdempotente() {
        prepararCuentasEncontradas();
        Transferencia anterior = new Transferencia(origen, destino, new BigDecimal("30.00"), "Cena", CLAVE);
        when(transferenciaRepository.findByIdempotencyKey(CLAVE)).thenReturn(Optional.of(anterior));

        ResultadoTransferencia r = servicio.transferir(1L, CLAVE, peticion("30.00"), ModoBloqueo.PESIMISTA, false);

        assertThat(r.repetida()).isTrue();
        assertThat(origen.getSaldo()).isEqualByComparingTo("100.00");   // ni un céntimo
        verifyNoInteractions(movimientoRepository, cache);
        verify(transferenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("IDEMPOTENCIA: misma clave pero OTRO importe -> 409 conflicto")
    void mismaClaveOtrosDatos() {
        prepararCuentasEncontradas();
        Transferencia anterior = new Transferencia(origen, destino, new BigDecimal("99.00"), "Otra", CLAVE);
        when(transferenciaRepository.findByIdempotencyKey(CLAVE)).thenReturn(Optional.of(anterior));

        assertThatThrownBy(() -> servicio.transferir(1L, CLAVE, peticion("30.00"), ModoBloqueo.PESIMISTA, false))
                .isInstanceOf(ConflictoException.class);

        verifyNoInteractions(movimientoRepository);
    }

    // ---------- utilidades ----------

    private void prepararCuentasEncontradas() {
        when(cuentaRepository.existsByIdAndUsuarioId(10L, 1L)).thenReturn(true);
        when(cuentaRepository.findIdByIban(IBAN_DESTINO)).thenReturn(Optional.of(20L));
        when(cuentaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(origen));
        when(cuentaRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(destino));
    }

    private static CrearTransferenciaRequest peticion(String importe) {
        return new CrearTransferenciaRequest(10L, IBAN_DESTINO, new BigDecimal(importe), "Cena");
    }

    /** Los id los pone la BBDD y no tienen setter; en un test unitario los ponemos "a mano". */
    private static <T> T conId(T entidad, Long id) {
        ReflectionTestUtils.setField(entidad, "id", id);
        return entidad;
    }
}
