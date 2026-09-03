package com.example.common.exception;

/**
 * Se lanza en el arranque cuando la base de datos tiene aplicada una migración más nueva que la
 * última que este build de la aplicación conoce.
 *
 * <p><b>Por qué hace falta un chequeo explícito.</b> Cada máquina se actualiza cuando quiere: la
 * app se autoactualiza contra GitHub Releases, así que después de que una migración corra en la
 * base compartida puede haber clientes con el build anterior operando durante días. Un cliente
 * viejo escribe sin las guardas de concurrencia y sin incrementar {@code version}: puede pisar en
 * silencio a uno actualizado. Y no hay protección gratis — Flyway trae
 * {@code ignoreFutureMigrations = true} por defecto, así que ve la migración desconocida, <b>no la
 * considera un error</b>, y la app arrancaría normalmente.</p>
 *
 * <p><b>Convive con {@code outOfOrder(true)}.</b> Una migración atrasada que se aplica después
 * (las ramas se pisaron los números) <em>no</em> es una base adelantada: el chequeo compara los
 * <b>máximos</b> de versión, no la continuidad de la secuencia.</p>
 */
public class EsquemaDesactualizadoException extends DatabaseException {

    public EsquemaDesactualizadoException(String message) {
        super(message);
    }
}
