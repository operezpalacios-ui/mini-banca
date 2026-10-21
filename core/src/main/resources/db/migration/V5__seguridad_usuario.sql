-- Lección 7: control de intentos de login (RF-1.2: tras 5 fallos, bloqueo temporal)
ALTER TABLE usuario ADD COLUMN intentos_fallidos INT NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD COLUMN bloqueado_hasta TIMESTAMPTZ;
