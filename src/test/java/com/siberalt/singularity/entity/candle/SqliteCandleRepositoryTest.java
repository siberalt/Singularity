package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

class SqliteCandleRepositoryTest {
    private static final String INSTRUMENT_UID = "TEST_INSTRUMENT";

    private Connection connection;
    private SqliteCandleRepository repository;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE candle (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    instrument_uid TEXT NOT NULL,
                    time_index INTEGER NOT NULL,
                    time INTEGER NOT NULL,
                    open_price INTEGER NOT NULL,
                    close_price INTEGER NOT NULL,
                    high_price INTEGER NOT NULL,
                    low_price INTEGER NOT NULL,
                    volume INTEGER NOT NULL,
                    volume_buy INTEGER NOT NULL DEFAULT 0,
                    volume_sell INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(instrument_uid, time)
                )
                """);
        }
        repository = new SqliteCandleRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Nested
    class NormalizeIndex {
        @Test
        void normalizesFreshlyInsertedCandlesFromZero() {
            // Свечи вставлены не по хронологии и все с одним и тем же placeholder-индексом,
            // как это происходит при параллельной миграции чанков
            insertPlaceholder(Instant.parse("2025-01-01T00:03:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:01:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:02:00Z"), 0);

            repository.normalizeIndex(INSTRUMENT_UID, Instant.parse("2025-01-01T00:00:00Z"));

            Assertions.assertEquals(0, indexAt("2025-01-01T00:01:00Z"));
            Assertions.assertEquals(1, indexAt("2025-01-01T00:02:00Z"));
            Assertions.assertEquals(2, indexAt("2025-01-01T00:03:00Z"));
        }

        @Test
        void appendingAfterExistingDataContinuesFromLastIndex() {
            // Уже нормализованные ранее свечи
            insertPlaceholder(Instant.parse("2025-01-01T00:01:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:02:00Z"), 1);
            repository.normalizeIndex(INSTRUMENT_UID, Instant.parse("2025-01-01T00:00:00Z"));

            // Новый хвост, добавленный отдельным (более поздним) прогоном миграции
            insertPlaceholder(Instant.parse("2025-01-01T00:04:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:03:00Z"), 0);

            // Нормализация вызывается только от точки нового хвоста - старые
            // индексы трогать не нужно и они не должны измениться
            repository.normalizeIndex(INSTRUMENT_UID, Instant.parse("2025-01-01T00:03:00Z"));

            Assertions.assertEquals(0, indexAt("2025-01-01T00:01:00Z"));
            Assertions.assertEquals(1, indexAt("2025-01-01T00:02:00Z"));
            Assertions.assertEquals(2, indexAt("2025-01-01T00:03:00Z"));
            Assertions.assertEquals(3, indexAt("2025-01-01T00:04:00Z"));
        }

        @Test
        void backfillingBeforeExistingDataShiftsSubsequentIndices() {
            insertPlaceholder(Instant.parse("2025-01-01T00:05:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:06:00Z"), 1);
            repository.normalizeIndex(INSTRUMENT_UID, Instant.parse("2025-01-01T00:00:00Z"));

            // Докачали более ранний период - новые свечи ложатся ПЕРЕД уже
            // существующими, значит их индексы должны сдвинуться
            insertPlaceholder(Instant.parse("2025-01-01T00:01:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:02:00Z"), 0);

            repository.normalizeIndex(INSTRUMENT_UID, Instant.parse("2025-01-01T00:00:00Z"));

            Assertions.assertEquals(0, indexAt("2025-01-01T00:01:00Z"));
            Assertions.assertEquals(1, indexAt("2025-01-01T00:02:00Z"));
            Assertions.assertEquals(2, indexAt("2025-01-01T00:05:00Z"));
            Assertions.assertEquals(3, indexAt("2025-01-01T00:06:00Z"));
        }

        @Test
        void differentInstrumentsAreNormalizedIndependently() {
            insertPlaceholder(INSTRUMENT_UID, Instant.parse("2025-01-01T00:01:00Z"), 0);
            insertPlaceholder("OTHER_INSTRUMENT", Instant.parse("2025-01-01T00:01:00Z"), 0);

            repository.normalizeIndex(INSTRUMENT_UID, Instant.parse("2025-01-01T00:00:00Z"));

            Assertions.assertEquals(0, indexAt(INSTRUMENT_UID, "2025-01-01T00:01:00Z"));
            // Другой инструмент не нормализовался - остался с placeholder-индексом
            Assertions.assertEquals(0, indexAt("OTHER_INSTRUMENT", "2025-01-01T00:01:00Z"));
        }
    }

    private void insertPlaceholder(Instant time, long placeholderIndex) {
        insertPlaceholder(INSTRUMENT_UID, time, placeholderIndex);
    }

    private void insertPlaceholder(String instrumentUid, Instant time, long placeholderIndex) {
        Candle candle = new Candle(
            instrumentUid,
            new TimePoint(placeholderIndex, time),
            Quotation.of(1.0),
            Quotation.of(1.0),
            Quotation.of(1.0),
            Quotation.of(1.0),
            1,
            0,
            0
        );
        repository.save(candle);
    }

    private long indexAt(String time) {
        return indexAt(INSTRUMENT_UID, time);
    }

    private long indexAt(String instrumentUid, String time) {
        String sql = "SELECT time_index FROM candle WHERE instrument_uid = ? AND time = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrumentUid);
            statement.setLong(2, Instant.parse(time).toEpochMilli());
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next(), "No candle found at " + time);
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
