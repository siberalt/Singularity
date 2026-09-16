package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.shared.TimePointRange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Candles in SQLite, keyed by our own instrument id - what any broker calls the instrument is not
 * this class's business.
 * <p>
 * Writes hold the connection's monitor for the whole of what they do. A JDBC connection carries one
 * transaction at a time, and several writers may share it - the migration saves chunks from a pool
 * of threads, and its checkpoint writes on the same connection. Unguarded, one thread's commit would
 * seal another's half-written batch and a rollback would wipe candles it never wrote. The checkpoint
 * repository takes the same monitor, which is what keeps "these candles are saved" and "this chunk
 * is done" from being recorded in the wrong order. Reads are left free: they change nothing, and a
 * read on a connection another thread is writing through sees at worst candles about to be committed.
 */
public class SqliteCandleRepository implements CandleRepository, CandleIndexNormalizer, AutoCloseable {
    private static final String COLUMNS =
        "time_index, time, open_price, close_price, high_price, low_price, volume, volume_buy, volume_sell";

    private final Connection connection;

    public SqliteCandleRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Candle> getAt(long instrumentId, Instant at) {
        String sql = "SELECT " + COLUMNS + " FROM candle WHERE instrument_id = ? AND time = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, at.toEpochMilli());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToCandle(resultSet, instrumentId));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при получении свечи по времени", e);
        }

        return Optional.empty();
    }

    @Override
    public List<Candle> findBeforeOrEqual(long instrumentId, Instant at, long amountBefore) {
        String sql = "SELECT " + COLUMNS + """
             FROM candle
            WHERE instrument_id = ? AND time <= ?
            ORDER BY time DESC
            LIMIT ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, at.toEpochMilli());
            statement.setLong(3, amountBefore + 1);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Candle> candles = collect(resultSet, instrumentId);
                // Реверсируем, чтобы получить порядок от старых к новым
                Collections.reverse(candles);

                // Если есть точное совпадение, удаляем предыдущие и возвращаем с текущей
                Optional<Candle> exactMatch = candles.stream()
                    .filter(c -> c.getTime().equals(at))
                    .findFirst();

                if (exactMatch.isPresent()) {
                    int index = candles.indexOf(exactMatch.get());
                    return candles.subList(0, index + 1);
                }

                return candles;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при поиске свечей до или равных времени", e);
        }
    }

    @Override
    public List<Candle> findAfterOrEqual(long instrumentId, Instant at, long amountAfter) {
        String sql = "SELECT " + COLUMNS + """
             FROM candle
            WHERE instrument_id = ? AND time >= ?
            ORDER BY time ASC
            LIMIT ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, at.toEpochMilli());
            statement.setLong(3, amountAfter);

            try (ResultSet resultSet = statement.executeQuery()) {
                return collect(resultSet, instrumentId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при поиске свечей после или равных времени", e);
        }
    }

    @Override
    public List<Candle> getPeriod(long instrumentId, Instant from, Instant to) {
        String sql = "SELECT " + COLUMNS + """
             FROM candle
            WHERE instrument_id = ? AND time >= ? AND time <= ?
            ORDER BY time ASC
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, from.toEpochMilli());
            statement.setLong(3, to.toEpochMilli());

            try (ResultSet resultSet = statement.executeQuery()) {
                return collect(resultSet, instrumentId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при получении свечей за период", e);
        }
    }

    @Override
    public List<Candle> findByPrice(long instrumentId, FindPriceParams params) {
        String column = priceColumn(params.priceField());
        String comparison = switch (params.comparisonOperator()) {
            case EQUAL -> "=";
            case LESS -> "<";
            case LESS_OR_EQUAL -> "<=";
            case MORE -> ">";
            case MORE_OR_EQUAL -> ">=";
            case NOT_EQUAL -> "<>";
        };

        String sql = "SELECT " + COLUMNS + " FROM candle WHERE instrument_id = ? AND time >= ? AND time <= ?"
            + " AND " + column + " " + comparison + " ? ORDER BY time ASC LIMIT ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, params.from().toEpochMilli());
            statement.setLong(3, params.to().toEpochMilli());
            statement.setLong(4, params.price().toLong());
            statement.setInt(5, params.maxCount());

            try (ResultSet resultSet = statement.executeQuery()) {
                return collect(resultSet, instrumentId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при поиске свечей по цене", e);
        }
    }

    /**
     * Column names are chosen here, never taken from the caller - the price field is an enum, so
     * the value spliced into the SQL can only be one of these four literals.
     */
    private String priceColumn(CandlePriceField priceField) {
        return switch (priceField) {
            case OPEN -> "open_price";
            case CLOSE -> "close_price";
            case HIGH -> "high_price";
            case LOW -> "low_price";
        };
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(long instrumentId, Instant from, Instant to) {
        String sql = """
            SELECT COUNT(*) as count, MAX(time) as last_time
            FROM candle
            WHERE instrument_id = ? AND time >= ? AND time <= ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
            statement.setLong(2, from.toEpochMilli());
            statement.setLong(3, to.toEpochMilli());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    long count = resultSet.getLong("count");
                    if (count == 0) {
                        return CandleRangeMetadata.EMPTY;
                    }
                    long lastTime = resultSet.getLong("last_time");
                    TimePointRange range = new TimePointRange(
                        new TimePoint(from),
                        new TimePoint(Instant.ofEpochMilli(lastTime))
                    );
                    return new CandleRangeMetadata(range, count);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при получении метаданных диапазона", e);
        }

        return CandleRangeMetadata.EMPTY;
    }

    private static final String UPSERT = "INSERT INTO candle (instrument_id, " + COLUMNS + """
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(instrument_id, time) DO UPDATE SET
            open_price = excluded.open_price,
            close_price = excluded.close_price,
            high_price = excluded.high_price,
            low_price = excluded.low_price,
            volume = excluded.volume,
            volume_buy = excluded.volume_buy,
            volume_sell = excluded.volume_sell
        """;

    @Override
    public void save(Candle candle) {
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                bind(statement, candle);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Ошибка при сохранении свечи", e);
            }
        }
    }

    /**
     * All of the batch or none of it. The monitor is held from switching auto-commit off to switching
     * it back on, so no other writer on this connection can have its statements folded into this
     * transaction or see this one committed half-way.
     */
    @Override
    public void saveBatch(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        synchronized (connection) {
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                    for (Candle candle : candles) {
                        if (candle == null) continue;

                        bind(statement, candle);
                        statement.addBatch();
                    }

                    statement.executeBatch();
                    connection.commit();
                } catch (SQLException e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackEx) {
                        e.addSuppressed(rollbackEx);
                    }
                    throw new RuntimeException("Ошибка при пакетном сохранении свечей", e);
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Ошибка при пакетном сохранении свечей", e);
            }
        }
    }

    /**
     * A candle has to say which instrument it is. One built without saying - {@link Candle#NO_INSTRUMENT}
     * - would otherwise be written against no instrument at all and could never be read back.
     */
    private void bind(PreparedStatement statement, Candle candle) throws SQLException {
        if (candle.instrumentId() == Candle.NO_INSTRUMENT) {
            throw new IllegalArgumentException("Свеча " + candle.getTime() + " не привязана к инструменту");
        }

        statement.setLong(1, candle.instrumentId());
        statement.setLong(2, candle.getIndex());
        statement.setLong(3, candle.getTime().toEpochMilli());
        statement.setLong(4, candle.open().toLong());
        statement.setLong(5, candle.close().toLong());
        statement.setLong(6, candle.high().toLong());
        statement.setLong(7, candle.low().toLong());
        statement.setLong(8, candle.volume());
        statement.setLong(9, candle.volumeBuy());
        statement.setLong(10, candle.volumeSell());
    }

    @Override
    public void delete(Candle candle) {
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM candle WHERE instrument_id = ? AND time = ?"
            )) {
                statement.setLong(1, candle.instrumentId());
                statement.setLong(2, candle.getTime().toEpochMilli());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Ошибка при удалении свечи", e);
            }
        }
    }

    @Override
    public void normalizeIndex(long instrumentId, Instant from) {
        long fromMillis = from.toEpochMilli();

        synchronized (connection) {
            // Индекс последней свечи перед from + 1 - быстрый seek по существующему
            // индексу (instrument_id, time), независимо от размера всей истории.
            // COUNT(*) по тому же условию дал бы то же значение, но потребовал бы
            // пересчитать все строки до from.
            String lastIndexSql = """
                SELECT time_index FROM candle
                WHERE instrument_id = ? AND time < ?
                ORDER BY time DESC
                LIMIT 1
                """;
            long baseIndex;
            try (PreparedStatement statement = connection.prepareStatement(lastIndexSql)) {
                statement.setLong(1, instrumentId);
                statement.setLong(2, fromMillis);
                try (ResultSet resultSet = statement.executeQuery()) {
                    baseIndex = resultSet.next() ? resultSet.getLong(1) + 1 : 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Ошибка при нормализации индексов свечей", e);
            }

            String updateSql = """
                WITH ranked AS (
                    SELECT id, ROW_NUMBER() OVER (ORDER BY time ASC) - 1 AS rn
                    FROM candle
                    WHERE instrument_id = ? AND time >= ?
                )
                UPDATE candle
                SET time_index = ? + (SELECT rn FROM ranked WHERE ranked.id = candle.id)
                WHERE instrument_id = ? AND time >= ?
                """;

            try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                statement.setLong(1, instrumentId);
                statement.setLong(2, fromMillis);
                statement.setLong(3, baseIndex);
                statement.setLong(4, instrumentId);
                statement.setLong(5, fromMillis);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Ошибка при нормализации индексов свечей", e);
            }
        }
    }

    private List<Candle> collect(ResultSet resultSet, long instrumentId) throws SQLException {
        List<Candle> candles = new ArrayList<>();

        while (resultSet.next()) {
            candles.add(mapResultSetToCandle(resultSet, instrumentId));
        }

        return candles;
    }

    private Candle mapResultSetToCandle(ResultSet resultSet, long instrumentId) throws SQLException {
        TimePoint timePoint = new TimePoint(
            resultSet.getLong("time_index"),
            Instant.ofEpochMilli(resultSet.getLong("time"))
        );

        return new Candle(
            instrumentId,
            timePoint,
            Quotation.fromLong(resultSet.getLong("open_price")),
            Quotation.fromLong(resultSet.getLong("close_price")),
            Quotation.fromLong(resultSet.getLong("high_price")),
            Quotation.fromLong(resultSet.getLong("low_price")),
            resultSet.getLong("volume"),
            resultSet.getLong("volume_buy"),
            resultSet.getLong("volume_sell")
        );
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
