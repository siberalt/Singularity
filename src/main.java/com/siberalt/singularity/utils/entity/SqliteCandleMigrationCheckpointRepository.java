package com.siberalt.singularity.utils.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранит прогресс миграции свечей как набор непересекающихся и не соседствующих
 * впритык временных диапазонов на инструмент. При отметке чанка выполненным
 * все диапазоны, пересекающиеся с ним или примыкающие к нему без зазора,
 * сливаются в один - это делает чекпойнт устойчивым к изменению размера
 * чанка и точки старта миграции между запусками: чанк считается уже
 * обработанным, если он целиком лежит внутри одного сохранённого диапазона,
 * а не только при точном совпадении границ.
 */
public class SqliteCandleMigrationCheckpointRepository implements CandleMigrationCheckpointRepository {
    private final Connection connection;

    public SqliteCandleMigrationCheckpointRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public boolean isDone(String instrumentUid, MigrationChunk chunk) {
        String sql = """
            SELECT 1 FROM candle_migration_range
            WHERE instrument_uid = ? AND range_from <= ? AND range_to >= ?
            LIMIT 1
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrumentUid);
            statement.setLong(2, chunk.from().toEpochMilli());
            statement.setLong(3, chunk.to().toEpochMilli());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при проверке чекпойнта миграции свечей", e);
        }
    }

    @Override
    public void markDone(String instrumentUid, MigrationChunk chunk) {
        long chunkFrom = chunk.from().toEpochMilli();
        long chunkTo = chunk.to().toEpochMilli();

        try {
            connection.setAutoCommit(false);
            try {
                long mergedFrom = chunkFrom;
                long mergedTo = chunkTo;

                // Диапазон пересекается или соседствует впритык с чанком, если
                // existing.from <= chunk.to И existing.to >= chunk.from
                String findOverlapping = """
                    SELECT range_from, range_to FROM candle_migration_range
                    WHERE instrument_uid = ? AND range_from <= ? AND range_to >= ?
                    """;

                List<Long> overlappingFroms = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(findOverlapping)) {
                    select.setString(1, instrumentUid);
                    select.setLong(2, chunkTo);
                    select.setLong(3, chunkFrom);

                    try (ResultSet resultSet = select.executeQuery()) {
                        while (resultSet.next()) {
                            long existingFrom = resultSet.getLong("range_from");
                            long existingTo = resultSet.getLong("range_to");
                            mergedFrom = Math.min(mergedFrom, existingFrom);
                            mergedTo = Math.max(mergedTo, existingTo);
                            overlappingFroms.add(existingFrom);
                        }
                    }
                }

                if (!overlappingFroms.isEmpty()) {
                    try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM candle_migration_range WHERE instrument_uid = ? AND range_from = ?"
                    )) {
                        for (long existingFrom : overlappingFroms) {
                            delete.setString(1, instrumentUid);
                            delete.setLong(2, existingFrom);
                            delete.addBatch();
                        }
                        delete.executeBatch();
                    }
                }

                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO candle_migration_range (instrument_uid, range_from, range_to) VALUES (?, ?, ?)"
                )) {
                    insert.setString(1, instrumentUid);
                    insert.setLong(2, mergedFrom);
                    insert.setLong(3, mergedTo);
                    insert.executeUpdate();
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException("Ошибка при сохранении чекпойнта миграции свечей", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при сохранении чекпойнта миграции свечей", e);
        }
    }
}
