CREATE TABLE movimiento (
    id         BIGSERIAL     PRIMARY KEY,
    cuenta_id  BIGINT        NOT NULL REFERENCES cuenta(id),
    tipo       VARCHAR(10)   NOT NULL CHECK (tipo IN ('CARGO', 'ABONO')),
    importe    NUMERIC(19,2) NOT NULL CHECK (importe > 0),
    concepto   VARCHAR(140),
    fecha      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- La consulta más habitual: "movimientos de ESTA cuenta, los más recientes primero"
CREATE INDEX idx_movimiento_cuenta_fecha ON movimiento(cuenta_id, fecha DESC);
