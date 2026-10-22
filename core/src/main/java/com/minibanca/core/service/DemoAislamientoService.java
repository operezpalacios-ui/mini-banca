package com.minibanca.core.service;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lección 9 (RF-7.5): DEMOSTRACIÓN de niveles de aislamiento. Usa la tabla de juguete
 * demo_contador, nunca los saldos.
 *
 * Simulamos a DOS personas trabajando a la vez sobre el mismo dato:
 *
 *   A: empieza su transacción y LEE el contador (0)
 *   B:      [otra transacción, otra conexión] suma 1 y hace COMMIT  -> contador = 1
 *   A: vuelve a LEER el contador         -> ¿ve 0 o 1?
 *   A: ESCRIBE "lo que leí al principio + 10"  -> ¿10 (pisando a B) o error?
 *
 *   READ COMMITTED  -> 2ª lectura = 1 ("lectura no repetible") y la escritura PISA a B:
 *                      valor final 10, el +1 de B se ha perdido ("lost update").
 *   REPEATABLE READ -> A ve una "foto" fija: 2ª lectura = 0. Al escribir, Postgres detecta que
 *   SERIALIZABLE       B cambió la fila y ABORTA a A con el error 40001. Valor final 1.
 *                      La app debería REINTENTAR la operación entera.
 *
 * ¿Y en las transferencias? Allí usamos READ COMMITTED (el de Postgres por defecto) + cerrojos
 * explícitos (FOR UPDATE) o @Version: es más predecible y no obliga a reintentar.
 *
 * Normalmente el nivel se pone con  @Transactional(isolation = Isolation.SERIALIZABLE).
 * Aquí usamos TransactionTemplate (transacciones "a mano") porque necesitamos meter la
 * transacción B justo EN MEDIO de la A, y ejecutamos  SET TRANSACTION ISOLATION LEVEL  como
 * primera sentencia: funciona igual aunque Neon use un pool de conexiones intermedio.
 */
@Service
public class DemoAislamientoService {

    private static final Logger log = LoggerFactory.getLogger(DemoAislamientoService.class);

    private final EntityManager em;
    private final TransactionTemplate txA;
    private final TransactionTemplate txB;

    public DemoAislamientoService(EntityManager em, PlatformTransactionManager tm) {
        this.em = em;
        this.txA = new TransactionTemplate(tm);
        this.txB = new TransactionTemplate(tm);
        // REQUIRES_NEW: B NO se une a la transacción de A. Spring "aparca" la de A, abre otra
        // conexión distinta para B, hace commit de B y luego devuelve el control a A.
        this.txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Map<String, Object> ejecutar(NivelAislamiento nivel) {
        // 0. Partimos siempre de contador = 0
        txB.executeWithoutResult(s -> em.createNativeQuery("UPDATE demo_contador SET valor = 0 WHERE id = 1")
                .executeUpdate());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nivelPedido", nivel);

        try {
            txA.executeWithoutResult(estadoA -> {
                // Debe ser lo PRIMERO de la transacción (Postgres no deja cambiarlo después)
                em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL " + nivel.sql()).executeUpdate();
                r.put("nivelReal", em.createNativeQuery("SHOW transaction_isolation").getSingleResult());

                int lecturaA1 = leer();
                r.put("1_A_lee", lecturaA1);

                txB.executeWithoutResult(estadoB -> em.createNativeQuery(
                        "UPDATE demo_contador SET valor = valor + 1 WHERE id = 1").executeUpdate());
                r.put("2_B_suma_1_y_hace_commit", "OK");

                int lecturaA2 = leer();
                r.put("3_A_vuelve_a_leer", lecturaA2);
                r.put("3_lecturaRepetible", lecturaA1 == lecturaA2);

                try {
                    em.createNativeQuery("UPDATE demo_contador SET valor = :v WHERE id = 1")
                            .setParameter("v", lecturaA1 + 10)
                            .executeUpdate();
                    r.put("4_A_escribe_lo_que_leyo_mas_10", "OK: ha escrito " + (lecturaA1 + 10));
                } catch (RuntimeException e) {
                    SQLException sql = buscarSqlException(e);
                    r.put("4_A_escribe_lo_que_leyo_mas_10", "ERROR " + (sql != null ? sql.getSQLState() : "?")
                            + ": " + (sql != null ? sql.getMessage() : e.getMessage()));
                    estadoA.setRollbackOnly();   // tras un error, Postgres solo acepta ROLLBACK
                }
            });
        } catch (RuntimeException e) {
            // Con SERIALIZABLE, a veces el conflicto no salta en el UPDATE sino al hacer COMMIT
            SQLException sql = buscarSqlException(e);
            r.put("5_commit_de_A", "ERROR " + (sql != null ? sql.getSQLState() : "?") + ": " + e.getMessage());
            log.info("Demo aislamiento: A no pudo hacer commit ({})", sql != null ? sql.getSQLState() : e);
        }

        int valorFinal = txB.execute(s -> leer());
        r.put("6_valor_final", valorFinal);
        r.put("conclusion", valorFinal == 10
                ? "LOST UPDATE: A pisó el +1 de B (lo esperable en READ COMMITTED sin cerrojos)"
                : "Datos protegidos: el +1 de B se conserva; A falló y tendría que reintentar");
        return r;
    }

    private int leer() {
        Object v = em.createNativeQuery("SELECT valor FROM demo_contador WHERE id = 1").getSingleResult();
        return ((Number) v).intValue();
    }

    private static SQLException buscarSqlException(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException s) return s;
            t = t.getCause();
        }
        return null;
    }
}
