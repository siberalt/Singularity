package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.db.initialize.FlywayDatabaseInitializer;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.SqliteInstrumentRepository;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class SqliteCandleRepositoryTest {
    private static final String BROKER = "tinkoff";
    private static final String INSTRUMENT_UID = "TEST_INSTRUMENT";
    private static final String OTHER_INSTRUMENT_UID = "OTHER_INSTRUMENT";

    private long instrumentId;
    private long otherInstrumentId;
    private Connection connection;
    private SqliteCandleRepository repository;

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
        // Свечи хранятся против нашего инструмента, поэтому он должен существовать до записи -
        // в рабочем коде это делает загрузчик перед миграцией.
        instruments.save(BROKER, new Instrument().setUid(INSTRUMENT_UID).setName(INSTRUMENT_UID).setLot(1).setCurrency("RUB"));
        instruments.save(BROKER, new Instrument().setUid(OTHER_INSTRUMENT_UID).setName(OTHER_INSTRUMENT_UID).setLot(1).setCurrency("RUB"));
        instrumentId = instruments.idOf(INSTRUMENT_UID).orElseThrow();
        otherInstrumentId = instruments.idOf(OTHER_INSTRUMENT_UID).orElseThrow();

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

            repository.normalizeIndex(instrumentId, Instant.parse("2025-01-01T00:00:00Z"));

            Assertions.assertEquals(0, indexAt("2025-01-01T00:01:00Z"));
            Assertions.assertEquals(1, indexAt("2025-01-01T00:02:00Z"));
            Assertions.assertEquals(2, indexAt("2025-01-01T00:03:00Z"));
        }

        @Test
        void appendingAfterExistingDataContinuesFromLastIndex() {
            // Уже нормализованные ранее свечи
            insertPlaceholder(Instant.parse("2025-01-01T00:01:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:02:00Z"), 1);
            repository.normalizeIndex(instrumentId, Instant.parse("2025-01-01T00:00:00Z"));

            // Новый хвост, добавленный отдельным (более поздним) прогоном миграции
            insertPlaceholder(Instant.parse("2025-01-01T00:04:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:03:00Z"), 0);

            // Нормализация вызывается только от точки нового хвоста - старые
            // индексы трогать не нужно и они не должны измениться
            repository.normalizeIndex(instrumentId, Instant.parse("2025-01-01T00:03:00Z"));

            Assertions.assertEquals(0, indexAt("2025-01-01T00:01:00Z"));
            Assertions.assertEquals(1, indexAt("2025-01-01T00:02:00Z"));
            Assertions.assertEquals(2, indexAt("2025-01-01T00:03:00Z"));
            Assertions.assertEquals(3, indexAt("2025-01-01T00:04:00Z"));
        }

        @Test
        void backfillingBeforeExistingDataShiftsSubsequentIndices() {
            insertPlaceholder(Instant.parse("2025-01-01T00:05:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:06:00Z"), 1);
            repository.normalizeIndex(instrumentId, Instant.parse("2025-01-01T00:00:00Z"));

            // Докачали более ранний период - новые свечи ложатся ПЕРЕД уже
            // существующими, значит их индексы должны сдвинуться
            insertPlaceholder(Instant.parse("2025-01-01T00:01:00Z"), 0);
            insertPlaceholder(Instant.parse("2025-01-01T00:02:00Z"), 0);

            repository.normalizeIndex(instrumentId, Instant.parse("2025-01-01T00:00:00Z"));

            Assertions.assertEquals(0, indexAt("2025-01-01T00:01:00Z"));
            Assertions.assertEquals(1, indexAt("2025-01-01T00:02:00Z"));
            Assertions.assertEquals(2, indexAt("2025-01-01T00:05:00Z"));
            Assertions.assertEquals(3, indexAt("2025-01-01T00:06:00Z"));
        }

        @Test
        void differentInstrumentsAreNormalizedIndependently() {
            insertPlaceholder(instrumentId, Instant.parse("2025-01-01T00:01:00Z"), 0);
            insertPlaceholder(otherInstrumentId, Instant.parse("2025-01-01T00:01:00Z"), 0);

            repository.normalizeIndex(instrumentId, Instant.parse("2025-01-01T00:00:00Z"));

            Assertions.assertEquals(0, indexAt(instrumentId, "2025-01-01T00:01:00Z"));
            // Другой инструмент не нормализовался - остался с placeholder-индексом
            Assertions.assertEquals(0, indexAt(otherInstrumentId, "2025-01-01T00:01:00Z"));
        }
    }

    /**
     * Which of the candle's four prices a search compares against - the difference between "the
     * price stood here at this instant" and "the market reached this level at some point in the
     * bar", which is what a limit order waits for.
     */
    @Nested
    class FindByPrice {
        private static final Instant TIME = Instant.parse("2025-01-01T00:01:00Z");

        @Test
        void matchesTheLowOfACandleThatOpenedAboveTheLevel() {
            // Opens at 12, never closes below it, but dips to 9 inside the bar.
            insertCandle(TIME, 12, 13, 9, 12);

            Assertions.assertEquals(1, matchCount(CandlePriceField.LOW, ComparisonOperator.LESS_OR_EQUAL, 9));
            // The same level compared against the open price finds nothing - the bar never opened there.
            Assertions.assertEquals(0, matchCount(CandlePriceField.OPEN, ComparisonOperator.LESS_OR_EQUAL, 9));
        }

        @Test
        void matchesTheHighOfACandleThatOpenedBelowTheLevel() {
            insertCandle(TIME, 8, 13, 7, 8);

            Assertions.assertEquals(1, matchCount(CandlePriceField.HIGH, ComparisonOperator.MORE_OR_EQUAL, 13));
            Assertions.assertEquals(0, matchCount(CandlePriceField.OPEN, ComparisonOperator.MORE_OR_EQUAL, 13));
        }

        @Test
        void comparesTheOpenPriceWhenNoFieldIsNamed() {
            insertCandle(TIME, 12, 13, 9, 12);

            List<Candle> found = repository.findByPrice(
                instrumentId,
                new FindPriceParams(
                    TIME.minusSeconds(60),
                    TIME.plusSeconds(60),
                    Quotation.of(12),
                    ComparisonOperator.EQUAL,
                    10
                )
            );

            Assertions.assertEquals(1, found.size());
        }

        private int matchCount(CandlePriceField priceField, ComparisonOperator operator, double price) {
            return repository.findByPrice(
                instrumentId,
                new FindPriceParams(
                    TIME.minusSeconds(60),
                    TIME.plusSeconds(60),
                    Quotation.of(price),
                    priceField,
                    operator,
                    10
                )
            ).size();
        }
    }

    /**
     * The migration saves batches from a pool of threads through one connection, and a connection
     * carries a single transaction: unguarded, one thread's commit seals another's half-written batch
     * and a rollback wipes candles it never wrote.
     */
    @Test
    void batchesSavedFromManyThreadsThroughOneConnectionAllLand() throws Exception {
        int threads = 8;
        int batchesPerThread = 25;
        int candlesPerBatch = 20;
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> work = new ArrayList<>();

        for (int thread = 0; thread < threads; thread++) {
            int first = thread * batchesPerThread;

            work.add(pool.submit(() -> {
                for (int batch = first; batch < first + batchesPerThread; batch++) {
                    List<Candle> candles = new ArrayList<>();

                    for (int minute = 0; minute < candlesPerBatch; minute++) {
                        Quotation price = Quotation.of(100);
                        Instant time = start.plusSeconds(60L * (batch * candlesPerBatch + minute));
                        candles.add(new Candle(instrumentId, new TimePoint(time), price, price, price, price, 1));
                    }

                    repository.saveBatch(candles);
                }
                return null;
            }));
        }

        for (Future<?> future : work) {
            future.get(60, TimeUnit.SECONDS);
        }

        pool.shutdown();

        int total = threads * batchesPerThread * candlesPerBatch;
        Assertions.assertEquals(total, repository.getPeriod(instrumentId, start, start.plusSeconds(60L * total)).size());
    }

    private void insertCandle(Instant time, double open, double high, double low, double close) {
        repository.save(
            new Candle(
                instrumentId,
                new TimePoint(0, time),
                Quotation.of(open),
                Quotation.of(close),
                Quotation.of(high),
                Quotation.of(low),
                1,
                0,
                0
            )
        );
    }

    private void insertPlaceholder(Instant time, long placeholderIndex) {
        insertPlaceholder(instrumentId, time, placeholderIndex);
    }

    private void insertPlaceholder(long instrumentId, Instant time, long placeholderIndex) {
        Candle candle = new Candle(
            instrumentId,
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
        return indexAt(instrumentId, time);
    }

    private long indexAt(long instrumentId, String time) {
        String sql = "SELECT time_index FROM candle WHERE instrument_id = ? AND time = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
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
