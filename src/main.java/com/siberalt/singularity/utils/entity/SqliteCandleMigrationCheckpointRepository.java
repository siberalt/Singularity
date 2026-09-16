package com.siberalt.singularity.utils.entity;

import com.siberalt.singularity.entity.instrument.InstrumentIdResolver;
import com.siberalt.singularity.entity.instrument.SqliteInstrumentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

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
    private final InstrumentIdResolver instruments;

    public SqliteCandleMigrationCheckpointRepository(Connection connection) {
        this(connection, new SqliteInstrumentRepository(connection));
    }

    /**
     * @param instruments чекпойнт хранится против нашего инструмента, а не против uid брокера -
     *                    иначе прогресс, набранный через одного брокера, ничего не сказал бы о той
     *                    же бумаге у другого
     */
    public SqliteCandleMigrationCheckpointRepository(Connection connection, InstrumentIdResolver instruments) {
        this.connection = connection;
        this.instruments = instruments;
    }

    /** Читать прогресс незнакомого инструмента можно: его просто нет. */
    private OptionalLong findId(String instrumentUid) {
        return instruments.idOf(instrumentUid);
    }

    private long idOf(String instrumentUid) {
        return instruments.idOf(instrumentUid).orElseThrow(() -> new IllegalStateException(
            "Инструмент " + instrumentUid + " неизвестен: сохраните его листинг через InstrumentRepository перед миграцией свечей"));
    }

    @Override
    public boolean isDone(String instrumentUid, MigrationChunk chunk) {
        OptionalLong instrumentId = findId(instrumentUid);

        if (instrumentId.isEmpty()) {
            return false;
        }

        String sql = """
            SELECT 1 FROM candle_migration_range
            WHERE instrument_id = ? AND range_from <= ? AND range_to >= ?
            LIMIT 1
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId.getAsLong());
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
        long instrumentId = idOf(instrumentUid);
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
                    WHERE instrument_id = ? AND range_from <= ? AND range_to >= ?
                    """;

                List<Long> overlappingFroms = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(findOverlapping)) {
                    select.setLong(1, instrumentId);
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
                        "DELETE FROM candle_migration_range WHERE instrument_id = ? AND range_from = ?"
                    )) {
                        for (long existingFrom : overlappingFroms) {
                            delete.setLong(1, instrumentId);
                            delete.setLong(2, existingFrom);
                            delete.addBatch();
                        }
                        delete.executeBatch();
                    }
                }

                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO candle_migration_range (instrument_id, range_from, range_to) VALUES (?, ?, ?)"
                )) {
                    insert.setLong(1, instrumentId);
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
