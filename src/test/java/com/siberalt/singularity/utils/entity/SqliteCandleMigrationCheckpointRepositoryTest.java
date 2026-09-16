package com.siberalt.singularity.utils.entity;

import com.siberalt.singularity.db.initialize.FlywayDatabaseInitializer;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.SqliteInstrumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class SqliteCandleMigrationCheckpointRepositoryTest {
    private static final String BROKER = "tinkoff";
    private static final String INSTRUMENT_UID = "TEST_INSTRUMENT";
    private static final String OTHER_INSTRUMENT_UID = "OTHER_INSTRUMENT";

    private long instrumentId;
    private long otherInstrumentId;
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

        SqliteInstrumentRepository instruments = new SqliteInstrumentRepository(connection);
        // Прогресс хранится против нашего инструмента, так что он должен быть заведён заранее.
        instruments.save(BROKER,
            new Instrument().setUid(INSTRUMENT_UID).setName(INSTRUMENT_UID).setLot(1).setCurrency("RUB"));

        instruments.save(BROKER,
            new Instrument().setUid(OTHER_INSTRUMENT_UID).setName(OTHER_INSTRUMENT_UID).setLot(1).setCurrency("RUB"));
        instrumentId = instruments.idOf(INSTRUMENT_UID).orElseThrow();
        otherInstrumentId = instruments.idOf(OTHER_INSTRUMENT_UID).orElseThrow();

        repository = new SqliteCandleMigrationCheckpointRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void isDoneReturnsFalseWhenNothingStored() {
        var chunk = chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z");
        Assertions.assertFalse(repository.isDone(instrumentId, chunk));
    }

    @Test
    void isDoneReturnsTrueForExactlyMarkedChunk() {
        var chunk = chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z");
        repository.markDone(instrumentId, chunk);
        Assertions.assertTrue(repository.isDone(instrumentId, chunk));
    }

    @Test
    void isDoneReturnsTrueForSubChunkOfLargerMarkedRange() {
        repository.markDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-10T00:00:00Z"));

        Assertions.assertTrue(repository.isDone(instrumentId, chunk("2025-01-03T00:00:00Z", "2025-01-05T00:00:00Z")));
    }

    @Test
    void adjacentChunksAreMergedIntoOneContinuousRange() {
        repository.markDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));
        repository.markDone(instrumentId, chunk("2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z"));

        // Ни один из двух отмеченных чанков не покрывает весь диапазон целиком -
        // но после слияния соседних диапазонов он должен считаться выполненным.
        Assertions.assertTrue(repository.isDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-03T00:00:00Z")));
    }

    @Test
    void overlappingChunksAreMerged() {
        repository.markDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-05T00:00:00Z"));
        repository.markDone(instrumentId, chunk("2025-01-03T00:00:00Z", "2025-01-08T00:00:00Z"));

        Assertions.assertTrue(repository.isDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-08T00:00:00Z")));
    }

    @Test
    void newChunkCanBridgeAGapBetweenTwoExistingRanges() {
        repository.markDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));
        repository.markDone(instrumentId, chunk("2025-01-05T00:00:00Z", "2025-01-06T00:00:00Z"));
        // Пока между ними разрыв - объединяющий диапазон не должен считаться готовым.
        Assertions.assertFalse(repository.isDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-06T00:00:00Z")));

        // Чанк, закрывающий разрыв и касающийся обеих сторон, должен слить все три в один.
        repository.markDone(instrumentId, chunk("2025-01-02T00:00:00Z", "2025-01-05T00:00:00Z"));

        Assertions.assertTrue(repository.isDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-06T00:00:00Z")));
    }

    @Test
    void chunksWithGapRemainSeparateAndUncoveredRangeIsNotDone() {
        repository.markDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));
        repository.markDone(instrumentId, chunk("2025-01-05T00:00:00Z", "2025-01-06T00:00:00Z"));

        Assertions.assertFalse(repository.isDone(instrumentId, chunk("2025-01-02T00:00:00Z", "2025-01-05T00:00:00Z")));
    }

    @Test
    void differentInstrumentsAreTrackedIndependently() {
        repository.markDone(instrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z"));

        Assertions.assertFalse(repository.isDone(otherInstrumentId, chunk("2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z")));
    }

    /**
     * Chunks are marked done from a pool of threads through one connection. Marking is a read, a
     * delete and an insert in one transaction, and a connection carries only one: unguarded, threads
     * commit and roll back each other's marks, and a chunk the migration finished is left undone.
     */
    @Test
    void chunksMarkedFromManyThreadsThroughOneConnectionAreAllDone() throws Exception {
        int threads = 8;
        int chunksPerThread = 50;
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> work = new ArrayList<>();

        for (int thread = 0; thread < threads; thread++) {
            int first = thread * chunksPerThread;

            work.add(pool.submit(() -> {
                for (int chunk = first; chunk < first + chunksPerThread; chunk++) {
                    repository.markDone(instrumentId, separateChunk(start, chunk));
                }
                return null;
            }));
        }

        for (Future<?> future : work) {
            future.get(60, TimeUnit.SECONDS);
        }

        pool.shutdown();

        for (int chunk = 0; chunk < threads * chunksPerThread; chunk++) {
            Assertions.assertTrue(repository.isDone(instrumentId, separateChunk(start, chunk)), "chunk " + chunk);
        }
    }

    /** An hour each, two hours apart, so no two of them merge into one range. */
    private static MigrationChunk separateChunk(Instant start, int chunk) {
        Instant from = start.plusSeconds(2 * 3600L * chunk);
        return new MigrationChunk(from, from.plusSeconds(3600));
    }

    private static MigrationChunk chunk(String from, String to) {
        return new MigrationChunk(Instant.parse(from), Instant.parse(to));
    }
}
