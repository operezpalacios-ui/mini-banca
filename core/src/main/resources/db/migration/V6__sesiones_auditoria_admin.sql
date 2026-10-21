-- Lección 8

-- RF-1.3: refresh tokens. Guardamos solo el HASH (SHA-256), nunca el token: si roban la tabla, no sirve.
CREATE TABLE refresh_token (
    id          BIGSERIAL    PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuario(id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ  NOT NULL,
    revocado    BOOLEAN      NOT NULL DEFAULT false,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_usuario ON refresh_token(usuario_id);

-- RF-6.2: un administrador puede desactivar un usuario
ALTER TABLE usuario ADD COLUMN activo BOOLEAN NOT NULL DEFAULT true;

-- RF-6.3: auditoría "quién hizo qué y cuándo"
CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    fecha       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_id  BIGINT,
    accion      VARCHAR(40)  NOT NULL,
    detalle     VARCHAR(500),
    trace_id    VARCHAR(64)
);
CREATE INDEX idx_audit_usuario_fecha ON audit_log(usuario_id, fecha DESC);
CREATE INDEX idx_audit_accion_fecha  ON audit_log(accion, fecha DESC);
