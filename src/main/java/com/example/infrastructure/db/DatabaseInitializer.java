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
            .load()
            .migrate();
        log.info("Migraciones Flyway aplicadas (schema + seeds)");
    }
}
