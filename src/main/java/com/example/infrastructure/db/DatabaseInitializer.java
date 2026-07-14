package com.example.infrastructure.db;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DatabaseInitializer() {}

    public static void inicializar() {
        Flyway.configure()
            .dataSource(ConnectionPool.getDataSource())
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            // La rama Lavadero ocupa V7-V12; si V13 (refactor volúmenes) llega antes
            // a producción, esto permite aplicar después las versiones "atrasadas".
            .outOfOrder(true)
            .load()
            .migrate();
        log.info("Migraciones Flyway aplicadas (schema + seeds)");
    }
}
