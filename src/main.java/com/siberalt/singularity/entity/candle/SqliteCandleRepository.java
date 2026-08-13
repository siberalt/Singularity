package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.shared.TimePointRange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

public class SqliteCandleRepository implements CandleRepository, AutoCloseable {
    private final Connection connection;

    public SqliteCandleRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        String sql = """
            SELECT instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume
            FROM candle
            WHERE instrument_uid = ? AND time = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrumentUid);
            statement.setLong(2, at.toEpochMilli());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToCandle(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при получении свечи по времени", e);
        }

        return Optional.empty();
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        String sql = """
            SELECT instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume
            FROM candle
            WHERE instrument_uid = ? AND time <= ?
            ORDER BY time DESC
            LIMIT ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrumentUid);
            statement.setLong(2, at.toEpochMilli());
            statement.setLong(3, amountBefore + 1);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Candle> candles = new ArrayList<>();
                while (resultSet.next()) {
                    candles.add(mapResultSetToCandle(resultSet));
                }
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
    public List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter) {
        String sql = """
            SELECT instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume
            FROM candle
            WHERE instrument_uid = ? AND time >= ?
            ORDER BY time ASC
            LIMIT ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrumentUid);
            statement.setLong(2, at.toEpochMilli());
            statement.setLong(3, amountAfter);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Candle> candles = new ArrayList<>();
                while (resultSet.next()) {
                    candles.add(mapResultSetToCandle(resultSet));
                }
                return candles;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при поиске свечей после или равных времени", e);
        }
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        String sql = """
            SELECT instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume
            FROM candle
            WHERE instrument_uid = ? AND time >= ? AND time <= ?
            ORDER BY time ASC
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrumentUid);
            statement.setLong(2, from.toEpochMilli());
            statement.setLong(3, to.toEpochMilli());

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Candle> candles = new ArrayList<>();
                while (resultSet.next()) {
                    candles.add(mapResultSetToCandle(resultSet));
                }
                return candles;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при получении свечей за период", e);
        }
    }

    @Override
    public List<Candle> findByOpenPrice(FindPriceParams params) {
        String sql = """
            SELECT instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume
            FROM candle
            WHERE instrument_uid = ? AND time >= ? AND time <= ?
            """;

        StringBuilder conditionBuilder = new StringBuilder();
        switch (params.comparisonOperator()) {
            case EQUAL -> conditionBuilder.append("open_price = ?");
            case LESS -> conditionBuilder.append("open_price < ?");
            case LESS_OR_EQUAL -> conditionBuilder.append("open_price <= ?");
            case MORE -> conditionBuilder.append("open_price > ?");
            case MORE_OR_EQUAL -> conditionBuilder.append("open_price >= ?");
            case NOT_EQUAL -> conditionBuilder.append("open_price <> ?");
        }

        sql += " AND " + conditionBuilder + " ORDER BY time ASC LIMIT ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            statement.setString(paramIndex++, params.instrumentUid());
            statement.setLong(paramIndex++, params.from().toEpochMilli());
            statement.setLong(paramIndex++, params.to().toEpochMilli());
            statement.setLong(paramIndex++, params.price().toLong());
            statement.setInt(paramIndex, params.maxCount());

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Candle> candles = new ArrayList<>();
                while (resultSet.next()) {
                    candles.add(mapResultSetToCandle(resultSet));
                }
                return candles;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при поиске свечей по цене открытия", e);
        }
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
        String sql = """
            SELECT COUNT(*) as count, MAX(time) as last_time
            FROM candle
            WHERE instrument_uid = ? AND time >= ? AND time <= ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrumentUid);
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

    @Override
    public void save(Candle candle) {
        String sql = """
            INSERT INTO candle (instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(instrument_uid, time_index) DO UPDATE SET
                open_price = excluded.open_price,
                close_price = excluded.close_price,
                high_price = excluded.high_price,
                low_price = excluded.low_price,
                volume = excluded.volume
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, candle.instrumentUid());
            statement.setLong(2, candle.getIndex());
            statement.setLong(3, candle.getTime().toEpochMilli());
            statement.setLong(4, candle.open().toLong());
            statement.setLong(5, candle.close().toLong());
            statement.setLong(6, candle.high().toLong());
            statement.setLong(7, candle.low().toLong());
            statement.setLong(8, candle.volume());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при сохранении свечи", e);
        }
    }

    @Override
    public void saveBatch(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO candle (instrument_uid, time_index, time, open_price, close_price, high_price, low_price, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(instrument_uid, time_index) DO UPDATE SET
                open_price = excluded.open_price,
                close_price = excluded.close_price,
                high_price = excluded.high_price,
                low_price = excluded.low_price,
                volume = excluded.volume
            """;

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Candle candle : candles) {
                    if (candle == null) continue;

                    statement.setString(1, candle.instrumentUid());
                    statement.setLong(2, candle.getIndex());
                    statement.setLong(3, candle.getTime().toEpochMilli());
                    statement.setLong(4, candle.open().toLong());
                    statement.setLong(5, candle.close().toLong());
                    statement.setLong(6, candle.high().toLong());
                    statement.setLong(7, candle.low().toLong());
                    statement.setLong(8, candle.volume());

                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw new RuntimeException("Ошибка при пакетном сохранении свечей", e);
            } finally {
                try {
                    connection.commit();
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при пакетном сохранении свечей", e);
        }
    }

    @Override
    public void delete(Candle candle) {
        String sql = "DELETE FROM candle WHERE instrument_uid = ? AND time_index = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, candle.instrumentUid());
            statement.setLong(2, candle.getIndex());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при удалении свечи", e);
        }
    }

    private Candle mapResultSetToCandle(ResultSet resultSet) throws SQLException {
        String instrumentUid = resultSet.getString("instrument_uid");
        long timeIndex = resultSet.getLong("time_index");
        long time = resultSet.getLong("time");
        long openPrice = resultSet.getLong("open_price");
        long closePrice = resultSet.getLong("close_price");
        long highPrice = resultSet.getLong("high_price");
        long lowPrice = resultSet.getLong("low_price");
        long volume = resultSet.getLong("volume");

        TimePoint timePoint = new TimePoint(timeIndex, Instant.ofEpochMilli(time));

        return new Candle(
            instrumentUid,
            timePoint,
            Quotation.fromLong(openPrice),
            Quotation.fromLong(closePrice),
            Quotation.fromLong(highPrice),
            Quotation.fromLong(lowPrice),
            volume
        );
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
