-- Idempotencia (RF-3.5): cada transferencia guarda la clave con la que se pidió.
-- El índice ÚNICO es la garantía final: aunque dos peticiones con la misma clave
-- se colaran a la vez, la base de datos solo dejaría guardar UNA.
ALTER TABLE transferencia ADD COLUMN idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX uq_transferencia_idempotency_key ON transferencia(idempotency_key);
