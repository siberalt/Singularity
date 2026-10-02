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

    /** Под каким uid этого брокера бумага уже числится, если числится. */
    private Optional<String> listedUnder(String brokerId, long instrumentId) {
        String sql = """
            SELECT broker_instrument_id FROM instrument_broker_listing
            WHERE broker_id = ? AND instrument_id = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, brokerId);
            statement.setLong(2, instrumentId);

            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.ofNullable(rows.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось прочитать листинг бумаги " + instrumentId, e);
        }
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

        long instrumentId = canonicalIdFor(brokerId, instrument);
        String sql = """
            INSERT INTO instrument_broker_listing (instrument_id, broker_id, broker_instrument_id, lot, currency)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(broker_id, broker_instrument_id)
            DO UPDATE SET instrument_id = excluded.instrument_id, lot = excluded.lot, currency = excluded.currency, broker_instrument_id = excluded.broker_instrument_id
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

    /**
     * Чья это бумага - по uid этого брокера, иначе по ISIN, иначе новая.
     * <p>
     * Порядок именно такой, и он важнее, чем выглядит. ISIN - идентификатор самой бумаги, и искать по нему
     * правильно, но у таблицы листингов есть второе ограничение: {@code UNIQUE(broker_id, instrument_id)} -
     * один листинг на брокера. Если спросить сперва ISIN, то бумага, пришедшая от брокера с <b>новым uid</b>
     * при том же ISIN, приводит к вставке второго листинга той же бумаги, и база отвечает
     * {@code SQLITE_CONSTRAINT_UNIQUE}, в котором не видно ни бумаги, ни причины. Поэтому uid спрашивается
     * первым: найденный листинг обновится на месте.
     * <p>
     * Если же uid неизвестен, а ISIN уже занят бумагой с другим листингом этого брокера - решать нечего и
     * угадывать нельзя. Это либо переоформленная бумага (и тогда её листинг надо перевести на новый uid
     * осознанно, вместе со свечами, которые лежат под старым), либо другой инструмент с тем же ISIN -
     * например другой класс паёв фонда, - и тогда делить с ним одну запись нельзя вовсе. Молча обновить
     * uid значит переписать владельца уже накопленных свечей, поэтому здесь бросается осмысленная ошибка.
     */
    private long canonicalIdFor(String brokerId, Instrument instrument) {
        OptionalLong byUid = idOf(instrument.getUid());
        Optional<CanonicalInstrument> byIsin = byUid.isPresent()
            ? Optional.empty()
            : instruments.findByIsin(instrument.getIsin());

        byIsin.ifPresent(found -> listedUnder(brokerId, found.getId()).ifPresent(uid -> {
            throw new IllegalStateException("ISIN " + instrument.getIsin() + " уже принадлежит бумаге "
                + found.getId() + " (" + found.getName() + "), а у неё листинг с другим uid: " + uid
                + ", новый - " + instrument.getUid() + ". Если это та же бумага, переоформленная у брокера,"
                + " переведите её листинг на новый uid вручную - свечи лежат под старым; если это другой"
                + " инструмент с тем же ISIN, ему нужна своя запись в instrument.");
        }));

        OptionalLong known = byUid.isPresent() ? byUid
            : byIsin.map(found -> OptionalLong.of(found.getId())).orElseGet(OptionalLong::empty);

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
