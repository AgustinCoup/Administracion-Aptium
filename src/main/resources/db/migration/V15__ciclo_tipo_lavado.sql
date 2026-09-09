-- Tipo de lavado por ciclo (LIMPIO / SUCIO / PODRIDO).
--
-- Sin DEFAULT a propósito: no hay filas que backfillear (las migraciones de lavadero V7-V14
-- todavía no se aplicaron en producción) y el único INSERT sobre esta tabla es el del DAO,
-- que siempre setea la columna. Sin DEFAULT, un INSERT que la olvide falla ruidosamente
-- en vez de inventar un valor.
--
-- Una sola sentencia y sin AFTER, para que corra igual en H2 (tests) y MySQL (producción).
ALTER TABLE ciclos_lavadero ADD COLUMN tipo_lavado VARCHAR(20) NOT NULL;
