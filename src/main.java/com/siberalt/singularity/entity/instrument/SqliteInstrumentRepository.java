package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Таблица instrument_broker_listing: чем инструмент является у конкретного брокера - его uid, лот и
 * валюта расчётов.
 * <p>
 * Сохранение листинга требует бумаги, к которой он относится, поэтому репозиторий получает
 * {@link CanonicalInstrumentRepository} и сам находит её: сперва по ISIN, потому что это и есть
 * идентификатор самой бумаги, затем по uid этого брокера - так заглушка, которую миграция завела по
 * одному лишь uid, дозаполняется, а не удваивается. Не нашлось ни того, ни другого - заводится новая.
 * <p>
 * Удаление снимает листинг, но не саму бумагу: то, что один брокер перестал её торговать, ничего не
 * говорит о ней самой, а свечи хранятся против неё.
 * <p>
 * Ответы {@link #idOf} кэшируются: их спрашивает каждая записываемая свеча, а измениться для уже
 * выданного id они не могут - листинг только добавляется или обновляется на месте.
 */
public class SqliteInstrumentRepository implements InstrumentRepository, InstrumentIdResolver {
    private static final String SELECT = """
        SELECT i.id, i.name, i.isin, i.instrument_type, l.broker_instrument_id, l.lot, l.currency
        FROM instrument_broker_listing l
        JOIN instrument i ON i.id = l.instrument_id
        """;

    private final Connection connection;
    private final CanonicalInstrumentRepository instruments;
    private final Map<String, Long> idsByBrokerUid = new ConcurrentHashMap<>();

    public SqliteInstrumentRepository(Connection connection) {
        this(connection, new SqliteCanonicalInstrumentRepository(connection));
    }

    public SqliteInstrumentRepository(Connection connection, CanonicalInstrumentRepository instruments) {
        this.connection = connection;
        this.instruments = instruments;
    }

    @Override
    public OptionalLong idOf(String brokerInstrumentId) {
        if (brokerInstrumentId == null) {
            return OptionalLong.empty();
        }

        Long cached = idsByBrokerUid.get(brokerInstrumentId);

        if (cached != null) {
            return OptionalLong.of(cached);
        }

        String sql = "SELECT instrument_id FROM instrument_broker_listing WHERE broker_instrument_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, brokerInstrumentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return OptionalLong.empty();
                }

                long id = resultSet.getLong(1);
                idsByBrokerUid.put(brokerInstrumentId, id);

                return OptionalLong.of(id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось найти инструмент по uid " + brokerInstrumentId, e);
        }
    }

    /**
     * What this broker calls the instrument with our id - the other way round from {@link #idOf}.
     * Candles are migrated by our id, and a broker can only be asked for them by its own name.
     * <p>
     * It lives here rather than on {@link ReadInstrumentRepository} for the same reason
     * {@link InstrumentIdResolver} does: an in-memory store of broker instruments has no ids of ours
     * to look anything up by.
     */
    public Optional<String> brokerInstrumentIdOf(String brokerId, long instrumentId) {
        String sql = "SELECT broker_instrument_id FROM instrument_broker_listing WHERE broker_id = ? AND instrument_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, brokerId);
            statement.setLong(2, instrumentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось найти листинг инструмента " + instrumentId + " у брокера " + brokerId, e);
        }
    }

    @Override
    public void save(String brokerId, Instrument instrument) {
        if (brokerId == null || instrument == null || instrument.getUid() == null) {
            throw new IllegalArgumentException("Листинг заводится под uid конкретного брокера");
        }

        long instrumentId = canonicalIdFor(instrument);
        String sql = """
            INSERT INTO instrument_broker_listing (instrument_id, broker_id, broker_instrument_id, lot, currency)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(broker_id, broker_instrument_id)
            DO UPDATE SET instrument_id = excluded.instrument_id, lot = excluded.lot, currency = excluded.currency
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instrumentId);
            statement.setString(2, brokerId);
            statement.setString(3, instrument.getUid());
            statement.setInt(4, Math.max(1, instrument.getLot()));
            statement.setString(5, null == instrument.getCurrency() ? "RUB" : instrument.getCurrency());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось сохранить листинг инструмента " + instrument.getUid(), e);
        }

        idsByBrokerUid.put(instrument.getUid(), instrumentId);
    }

    private long canonicalIdFor(Instrument instrument) {
        Optional<CanonicalInstrument> byIsin = instruments.findByIsin(instrument.getIsin());
        OptionalLong known = byIsin.map(found -> OptionalLong.of(found.getId()))
            .orElseGet(() -> idOf(instrument.getUid()));

        CanonicalInstrument canonical = new CanonicalInstrument()
            .setIsin(instrument.getIsin())
            .setName(nameOf(instrument))
            .setInstrumentType(null == instrument.getInstrumentType()
                ? InstrumentType.UNSPECIFIED
                : instrument.getInstrumentType());

        if (known.isPresent()) {
            canonical.setId(known.getAsLong());
        }

        return instruments.save(canonical).getId();
    }

    @Override
    public void delete(String brokerId, Instrument instrument) {
        String sql = "DELETE FROM instrument_broker_listing WHERE broker_id = ? AND broker_instrument_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, brokerId);
            statement.setString(2, instrument.getUid());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось удалить листинг инструмента " + instrument.getUid(), e);
        }

        idsByBrokerUid.remove(instrument.getUid());
    }

    @Override
    public Optional<Instrument> get(String brokerId, String id) {
        try (PreparedStatement statement = connection.prepareStatement(
            SELECT + "WHERE l.broker_id = ? AND l.broker_instrument_id = ?"
        )) {
            statement.setString(1, brokerId);
            statement.setString(2, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(instrumentOf(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось прочитать листинг инструмента " + id, e);
        }
    }

    @Override
    public Iterable<Instrument> getByType(String brokerId, InstrumentType instrumentType) {
        return listBy(SELECT + "WHERE l.broker_id = ? AND i.instrument_type = ? ORDER BY i.name",
            statement -> {
                statement.setString(1, brokerId);
                statement.setString(2, instrumentType.name());
            });
    }

    @Override
    public List<Instrument> getAll(String brokerId) {
        return listBy(SELECT + "WHERE l.broker_id = ? ORDER BY i.name",
            statement -> statement.setString(1, brokerId));
    }

    private List<Instrument> listBy(String sql, ParameterBinder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Instrument> instrumentList = new ArrayList<>();

                while (resultSet.next()) {
                    instrumentList.add(instrumentOf(resultSet));
                }

                return instrumentList;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось перечислить листинги инструментов", e);
        }
    }

    private Instrument instrumentOf(ResultSet resultSet) throws SQLException {
        return new Instrument()
            .setName(resultSet.getString("name"))
            .setIsin(resultSet.getString("isin"))
            .setInstrumentType(SqliteCanonicalInstrumentRepository.typeOf(resultSet.getString("instrument_type")))
            .setUid(resultSet.getString("broker_instrument_id"))
            .setLot(resultSet.getInt("lot"))
            .setCurrency(resultSet.getString("currency"));
    }

    /** Имя обязательно для таблицы; брокер, который его не дал, оставляет вместо него uid. */
    private String nameOf(Instrument instrument) {
        return null == instrument.getName() || instrument.getName().isBlank()
            ? instrument.getUid()
            : instrument.getName();
    }

    @FunctionalInterface
    private interface ParameterBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
