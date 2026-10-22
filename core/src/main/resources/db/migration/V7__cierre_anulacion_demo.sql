-- Lección 9: completar el backend

-- RF-2.3: cerrar una cuenta (no se borra NUNCA: un banco guarda el histórico)
ALTER TABLE cuenta ADD COLUMN cerrada_en TIMESTAMPTZ;

-- RF-3.6: un administrador puede ANULAR una transferencia.
-- Tampoco se borra: se marca como ANULADA y se crean movimientos que la compensan.
ALTER TABLE transferencia
    ADD COLUMN estado           VARCHAR(20)  NOT NULL DEFAULT 'COMPLETADA'
                                CHECK (estado IN ('COMPLETADA', 'ANULADA')),
    ADD COLUMN anulada_en       TIMESTAMPTZ,
    ADD COLUMN anulada_por      BIGINT       REFERENCES usuario(id),
    ADD COLUMN motivo_anulacion VARCHAR(200);

-- RF-4.2: filtrar movimientos por tipo dentro de una cuenta, ordenados por fecha
CREATE INDEX idx_movimiento_cuenta_tipo_fecha ON movimiento(cuenta_id, tipo, fecha DESC);

-- RF-7.5: tabla de JUGUETE para la demo de niveles de aislamiento.
-- Así experimentamos sin tocar ni un céntimo de los saldos reales.
CREATE TABLE demo_contador (
    id    INT  PRIMARY KEY,
    valor INT  NOT NULL
);
INSERT INTO demo_contador (id, valor) VALUES (1, 0);
