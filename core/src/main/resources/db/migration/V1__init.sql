CREATE TABLE usuario (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nombre        VARCHAR(100) NOT NULL,
    rol           VARCHAR(20)  NOT NULL,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cuenta (
    id          BIGSERIAL     PRIMARY KEY,
    usuario_id  BIGINT        NOT NULL REFERENCES usuario(id),
    iban        VARCHAR(34)   NOT NULL UNIQUE,
    alias       VARCHAR(100),
    divisa      VARCHAR(3)    NOT NULL,
    saldo       NUMERIC(19,2) NOT NULL DEFAULT 0,
    estado      VARCHAR(20)   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    creado_en   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_cuenta_usuario ON cuenta(usuario_id);
