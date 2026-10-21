CREATE TABLE transferencia (
    id                 BIGSERIAL     PRIMARY KEY,
    cuenta_origen_id   BIGINT        NOT NULL REFERENCES cuenta(id),
    cuenta_destino_id  BIGINT        NOT NULL REFERENCES cuenta(id),
    importe            NUMERIC(19,2) NOT NULL CHECK (importe > 0),
    concepto           VARCHAR(140),
    fecha              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_transferencia_cuentas_distintas CHECK (cuenta_origen_id <> cuenta_destino_id)
);

CREATE INDEX idx_transferencia_origen  ON transferencia(cuenta_origen_id);
CREATE INDEX idx_transferencia_destino ON transferencia(cuenta_destino_id);

-- Red de seguridad: aunque el código Java fallara, la BBDD NUNCA aceptará un saldo negativo.
ALTER TABLE cuenta ADD CONSTRAINT chk_cuenta_saldo_no_negativo CHECK (saldo >= 0);
