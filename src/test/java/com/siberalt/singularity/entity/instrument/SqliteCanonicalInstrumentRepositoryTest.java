package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.db.initialize.FlywayDatabaseInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteCanonicalInstrumentRepositoryTest {
    private Connection connection;
    private SqliteCanonicalInstrumentRepository repository;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        String jdbcUrl = "jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared";
        connection = DriverManager.getConnection(jdbcUrl);
        new FlywayDatabaseInitializer().migrate(jdbcUrl);

        repository = new SqliteCanonicalInstrumentRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void givesAnIdToAnInstrumentItHasNotSeen() {
        CanonicalInstrument saved = repository.save(instrument("Сбербанк", "RU0009029540"));

        assertNotNull(saved.getId());
        assertEquals("Сбербанк", repository.get(saved.getId()).orElseThrow().getName());
        assertEquals(saved.getId(), repository.findByIsin("RU0009029540").orElseThrow().getId());
    }

    @Test
    void savingWithAnIdUpdatesInsteadOfAddingAnother() {
        CanonicalInstrument saved = repository.save(instrument("Сбербанк", "RU0009029540"));

        repository.save(saved.setName("Сбер"));

        assertEquals("Сбер", repository.get(saved.getId()).orElseThrow().getName());
        assertEquals(1, repository.getAll().size());
    }

    /**
     * У инструмента, заведённого миграцией по одному лишь uid, ISIN нет. Когда брокер наконец назовёт
     * бумагу, а в другой раз промолчит, уже записанный ISIN должен уцелеть - иначе бумага перестанет
     * узнаваться у второго брокера.
     */
    @Test
    void keepsAnIsinItAlreadyKnowsWhenSavedWithoutOne() {
        CanonicalInstrument saved = repository.save(instrument("Сбербанк", "RU0009029540"));

        repository.save(saved.setIsin(null));

        assertEquals("RU0009029540", repository.get(saved.getId()).orElseThrow().getIsin());
    }

    @Test
    void findsNothingByAnIsinNobodySaved() {
        assertTrue(repository.findByIsin("RU0000000000").isEmpty());
        assertTrue(repository.findByIsin(null).isEmpty());
        assertTrue(repository.get(42).isEmpty());
    }

    @Test
    void listsWhatItHoldsAndForgetsWhatIsDeleted() {
        repository.save(instrument("Сбербанк", "RU0009029540"));
        CanonicalInstrument other = repository.save(instrument("Газпром", "RU0007661625"));

        assertEquals(List.of("Газпром", "Сбербанк"),
            repository.getAll().stream().map(CanonicalInstrument::getName).toList());

        repository.delete(other);

        assertEquals(List.of("Сбербанк"),
            repository.getAll().stream().map(CanonicalInstrument::getName).toList());
    }

    private CanonicalInstrument instrument(String name, String isin) {
        return new CanonicalInstrument()
            .setName(name)
            .setIsin(isin)
            .setInstrumentType(InstrumentType.SHARE);
    }
}
