package com.siberalt.singularity.utils.entity;

import com.siberalt.singularity.db.initialize.FlywayDatabaseInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

class SqliteCandleMigrationCheckpointRepositoryTest {
    private static final String INSTRUMENT_UID = "TEST_INSTRUMENT";

    private Connection connection;
    private SqliteCandleMigrationCheckpointRepository repository;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        // Общая для процесса in-memory база под уникальным именем: держим соединение
        // открытым, пока идут Flyway-миграции (иначе SQLite уничтожит пустую in-memory
        // базу сразу после закрытия последнего подключения к ней), и используем
        // уникальное имя на тест, чтобы тесты не делили состояние друг с другом.
        String jdbcUrl = "jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared";
        connection = DriverManager.getConnection(jdbcUrl);
        new FlywayDatabaseInitializer().migrate(jdbcUrl);

        repository = new SqliteCandleMigrationCheckpointRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void isDoneReturnsFalseWhenNothingStored() {
        var chunk = chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z");
        Assertions.assertFalse(repository.isDone(INSTRUMENT_UID, chunk));
    }

    @Test
    void isDoneReturnsTrueForExactlyMarkedChunk() {
        var chunk = chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z");
        repository.markDone(INSTRUMENT_UID, chunk);
        Assertions.assertTrue(repository.isDone(INSTRUMENT_UID, chunk));
    }

    @Test
    void isDoneReturnsTrueForSubChunkOfLargerMarkedRange() {
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-10T00:00:00Z"));

        Assertions.assertTrue(repository.isDone(INSTRUMENT_UID, chunk("2025-01-03T00:00:00Z", "2025-01-05T00:00:00Z")));
    }

    @Test
    void adjacentChunksAreMergedIntoOneContinuousRange() {
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z"));

        // Ни один из двух отмеченных чанков не покрывает весь диапазон целиком -
        // но после слияния соседних диапазонов он должен считаться выполненным.
        Assertions.assertTrue(repository.isDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-03T00:00:00Z")));
    }

    @Test
    void overlappingChunksAreMerged() {
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-05T00:00:00Z"));
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-03T00:00:00Z", "2025-01-08T00:00:00Z"));

        Assertions.assertTrue(repository.isDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-08T00:00:00Z")));
    }

    @Test
    void newChunkCanBridgeAGapBetweenTwoExistingRanges() {
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-05T00:00:00Z", "2025-01-06T00:00:00Z"));
        // Пока между ними разрыв - объединяющий диапазон не должен считаться готовым.
        Assertions.assertFalse(repository.isDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-06T00:00:00Z")));

        // Чанк, закрывающий разрыв и касающийся обеих сторон, должен слить все три в один.
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-02T00:00:00Z", "2025-01-05T00:00:00Z"));

        Assertions.assertTrue(repository.isDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-06T00:00:00Z")));
    }

    @Test
    void chunksWithGapRemainSeparateAndUncoveredRangeIsNotDone() {
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-05T00:00:00Z", "2025-01-06T00:00:00Z"));

        Assertions.assertFalse(repository.isDone(INSTRUMENT_UID, chunk("2025-01-02T00:00:00Z", "2025-01-05T00:00:00Z")));
    }

    @Test
    void differentInstrumentsAreTrackedIndependently() {
        repository.markDone(INSTRUMENT_UID, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));

        Assertions.assertFalse(repository.isDone("OTHER_INSTRUMENT", chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z")));
    }

    private static MigrationChunk chunk(String from, String to) {
        return new MigrationChunk(Instant.parse(from), Instant.parse(to));
    }
}
