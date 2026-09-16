package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Таблица instrument: бумага сама по себе, без того, что о ней думает конкретный брокер.
 * <p>
 * Лот и валюта сюда не попадают намеренно - они у разных брокеров разные даже для одной бумаги и
 * живут в листинге ({@link SqliteInstrumentRepository}).
 */
public class SqliteCanonicalInstrumentRepository implements CanonicalInstrumentRepository {
    private final Connection connection;

    public SqliteCanonicalInstrumentRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public CanonicalInstrument save(CanonicalInstrument instrument) {
        if (instrument == null) {
            throw new IllegalArgumentException("Нечего сохранять");
        }

        return instrument.getId() == null ? insert(instrument) : update(instrument);
    }

    private CanonicalInstrument insert(CanonicalInstrument instrument) {
        String sql = "INSERT INTO instrument (isin, name, instrument_type) VALUES (?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, instrument.getIsin());
            statement.setString(2, instrument.getName());
            statement.setString(3, typeNameOf(instrument));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("База не вернула идентификатор нового инструмента");
                }

                return instrument.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось сохранить инструмент " + instrument.getName(), e);
        }
    }

    /**
     * ISIN обновляется только если он известен: у инструмента, заведённого миграцией по одному лишь
     * uid, его нет, и затирать им уже записанный было бы потерей.
     */
    private CanonicalInstrument update(CanonicalInstrument instrument) {
        String sql = "UPDATE instrument SET isin = COALESCE(?, isin), name = ?, instrument_type = ? WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instrument.getIsin());
            statement.setString(2, instrument.getName());
            statement.setString(3, typeNameOf(instrument));
            statement.setLong(4, instrument.getId());
            statement.executeUpdate();

            return instrument;
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось обновить инструмент " + instrument.getId(), e);
        }
    }

    @Override
    public void delete(CanonicalInstrument instrument) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM instrument WHERE id = ?")) {
            statement.setLong(1, instrument.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось удалить инструмент " + instrument.getId(), e);
        }
    }

    @Override
    public Optional<CanonicalInstrument> get(long id) {
        return findBy("SELECT id, isin, name, instrument_type FROM instrument WHERE id = ?", statement ->
            statement.setLong(1, id));
    }

    @Override
    public Optional<CanonicalInstrument> findByIsin(String isin) {
        if (isin == null || isin.isBlank()) {
            return Optional.empty();
        }

        return findBy("SELECT id, isin, name, instrument_type FROM instrument WHERE isin = ?", statement ->
            statement.setString(1, isin));
    }

    @Override
    public List<CanonicalInstrument> getAll() {
        String sql = "SELECT id, isin, name, instrument_type FROM instrument ORDER BY name";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<CanonicalInstrument> instruments = new ArrayList<>();

            while (resultSet.next()) {
                instruments.add(instrumentOf(resultSet));
            }

            return instruments;
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось перечислить инструменты", e);
        }
    }

    private Optional<CanonicalInstrument> findBy(String sql, ParameterBinder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(instrumentOf(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось прочитать инструмент", e);
        }
    }

    private CanonicalInstrument instrumentOf(ResultSet resultSet) throws SQLException {
        return new CanonicalInstrument()
            .setId(resultSet.getLong("id"))
            .setIsin(resultSet.getString("isin"))
            .setName(resultSet.getString("name"))
            .setInstrumentType(typeOf(resultSet.getString("instrument_type")));
    }

    private String typeNameOf(CanonicalInstrument instrument) {
        return (null == instrument.getInstrumentType() ? InstrumentType.UNSPECIFIED : instrument.getInstrumentType())
            .name();
    }

    /** Тип, который база не знает, читается как UNSPECIFIED: строка в таблице важнее, чем её ярлык. */
    static InstrumentType typeOf(String name) {
        try {
            return InstrumentType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return InstrumentType.UNSPECIFIED;
        }
    }

    @FunctionalInterface
    private interface ParameterBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
