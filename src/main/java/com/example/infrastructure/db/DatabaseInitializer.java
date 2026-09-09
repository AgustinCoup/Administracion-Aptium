package com.example.infrastructure.db;

import com.example.common.constants.Constantes;
import com.example.common.exception.EsquemaDesactualizadoException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DatabaseInitializer() {}

    public static void inicializar() {
        Flyway flyway = Flyway.configure()
            .dataSource(ConnectionPool.getDataSource())
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            // La rama Lavadero ocupa V7-V12; si V13 (refactor volúmenes) llega antes
            // a producción, esto permite aplicar después las versiones "atrasadas".
            .outOfOrder(true)
            .load();

        flyway.migrate();
        verificarEsquemaNoAdelantado(flyway);
        log.info("Migraciones Flyway aplicadas (schema + seeds)");
    }

    /**
     * Aborta el arranque si la base tiene aplicada una migración más nueva que la última que
     * este build trae en {@code classpath:db/migration}.
     *
     * <p>Corre <b>después</b> de {@code migrate()}: cualquier migración local que estuviera
     * pendiente —incluida una fuera de orden por {@code outOfOrder(true)}— ya se aplicó, así que
     * lo único que puede quedar por encima del máximo local es una migración que este build no
     * conoce. Ese es exactamente el caso peligroso: un cliente viejo escribiendo sin guardas
     * contra una base ya migrada por otro.</p>
     *
     * <p>La comparación es de <b>máximos de versión</b>, no de continuidad: una migración
     * atrasada aplicada después (V8 tardía mientras el build ya va por V21) deja
     * {@code maxAplicado <= maxLocal} y no dispara nada.</p>
     */
    static void verificarEsquemaNoAdelantado(Flyway flyway) {
        MigrationInfo[] todas = flyway.info().all();
        MigrationVersion maxLocal = maxVersion(todas, false);
        MigrationVersion maxAplicado = maxVersion(todas, true);
        verificarNoAdelantado(maxAplicado, maxLocal);
    }

    /**
     * El núcleo comparable del chequeo, aislado para poder testearlo sin base.
     *
     * @param maxAplicado versión más alta registrada en {@code flyway_schema_history}
     * @param maxLocal    versión más alta que este build resuelve desde el classpath
     */
    static void verificarNoAdelantado(MigrationVersion maxAplicado, MigrationVersion maxLocal) {
        if (maxAplicado == null || maxLocal == null) {
            return;
        }
        if (maxAplicado.compareTo(maxLocal) > 0) {
            log.error("La base está en la migración {} y este build sólo conoce hasta la {}.",
                    maxAplicado, maxLocal);
            throw new EsquemaDesactualizadoException(Constantes.Mensajes.ESQUEMA_DESACTUALIZADO);
        }
    }

    /**
     * Máximo de versión sobre {@code info.all()}.
     *
     * @param soloAplicadas {@code true} → sólo filas ya presentes en el historial;
     *                       {@code false} → sólo migraciones que este build resuelve localmente
     *                       (se descartan las {@code FUTURE_*} y {@code MISSING_*}, que existen
     *                       en el historial pero no en el classpath de este build)
     */
    private static MigrationVersion maxVersion(MigrationInfo[] infos, boolean soloAplicadas) {
        MigrationVersion max = null;
        for (MigrationInfo mi : infos) {
            MigrationVersion version = mi.getVersion();
            if (version == null) {
                continue; // repeatables
            }
            if (soloAplicadas) {
                if (mi.getInstalledOn() == null) {
                    continue;
                }
            } else {
                MigrationState estado = mi.getState();
                if (estado == MigrationState.FUTURE_SUCCESS || estado == MigrationState.FUTURE_FAILED
                        || estado == MigrationState.MISSING_SUCCESS
                        || estado == MigrationState.MISSING_FAILED) {
                    continue;
                }
            }
            if (max == null || version.compareTo(max) > 0) {
                max = version;
            }
        }
        return max;
    }
}
