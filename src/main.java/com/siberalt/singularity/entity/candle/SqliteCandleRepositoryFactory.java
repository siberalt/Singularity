package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.Factory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteCandleRepositoryFactory implements Factory {
    private static final String DEFAULT_DB_PATH = "candles.db";

    public SqliteCandleRepository create(String dbPath) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection connection = DriverManager.getConnection(dbPath);
            return new SqliteCandleRepository(connection);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC драйвер не найден", e);
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка подключения к SQLite базе данных", e);
        }
    }

    @Override
    public SqliteCandleRepository create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        String dbPath = (String) serviceDetails.config().get("dbPath");
        if (dbPath == null || dbPath.trim().isEmpty()) {
            dbPath = DEFAULT_DB_PATH;
        }
        return create(dbPath);
    }
}
