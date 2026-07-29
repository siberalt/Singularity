package com.siberalt.singularity.db.initialize;

import org.flywaydb.core.Flyway;

public class FlywayDatabaseInitializer {
    public void migrate(String jdbcUrl) {
        Flyway.configure()
            .dataSource(jdbcUrl, null, null) // SQLite не требует логина/пароля
            .load()
            .migrate();
    }
}
